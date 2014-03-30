/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.java;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.documentation.DocumentationManagerProtocol;
import com.intellij.codeInsight.documentation.PlatformDocumentationUtil;
import com.intellij.codeInsight.editorActions.CodeDocumentationUtil;
import com.intellij.codeInsight.javadoc.JavaDocExternalFilter;
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.LangBundle;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.documentation.CodeDocumentationProvider;
import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.*;
import com.intellij.psi.impl.beanProperties.BeanPropertyElement;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author Maxim.Mossienko
 */
public class JavaDocumentationProvider implements CodeDocumentationProvider, ExternalDocumentationProvider {
  private static final Logger LOG = Logger.getInstance("#" + JavaDocumentationProvider.class.getName());
  private static final String LINE_SEPARATOR = "\n";

  @NonNls private static final String PARAM_TAG = "@param";
  @NonNls private static final String RETURN_TAG = "@return";
  @NonNls private static final String THROWS_TAG = "@throws";
  @NonNls public static final String HTML_EXTENSION = ".html";
  @NonNls public static final String PACKAGE_SUMMARY_FILE = "package-summary.html";

  @Override
     public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
       if (element instanceof PsiClass) {
         return generateClassInfo((PsiClass)element);
       }
       else if (element instanceof PsiMethod) {
         return generateMethodInfo((PsiMethod)element, calcSubstitutor(originalElement));
       }
       else if (element instanceof PsiField) {
         return generateFieldInfo((PsiField)element, calcSubstitutor(originalElement));
       }
       else if (element instanceof PsiVariable) {
         return generateVariableInfo((PsiVariable)element);
       }
       else if (element instanceof PsiPackage) {
         return generatePackageInfo((PsiPackage)element);
       }
       else if (element instanceof BeanPropertyElement) {
         return generateMethodInfo(((BeanPropertyElement) element).getMethod(), PsiSubstitutor.EMPTY);
       }
       return null;
     }

  private static PsiSubstitutor calcSubstitutor(PsiElement originalElement) {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (originalElement instanceof PsiReferenceExpression) {
      LOG.assertTrue(originalElement.isValid());
      substitutor = ((PsiReferenceExpression)originalElement).advancedResolve(true).getSubstitutor();
    }
    return substitutor;
  }

  @Override
  public List<String> getUrlFor(final PsiElement element, final PsiElement originalElement) {
    return getExternalJavaDocUrl(element);
  }

  private static void newLine(StringBuilder buffer) {
    // Don't know why space has to be added after newline for good text alignment...
    buffer.append("\n ");
  }

  private static void generateInitializer(StringBuilder buffer, PsiVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    if (initializer != null) {
      JavaDocInfoGenerator.appendExpressionValue(buffer, initializer, " = ");
      PsiExpression constantInitializer = JavaDocInfoGenerator.calcInitializerExpression(variable);
      if (constantInitializer != null) {
        buffer.append("\n");
        JavaDocInfoGenerator.appendExpressionValue(buffer, constantInitializer, CodeInsightBundle.message("javadoc.resolved.value"));
      }
    }
  }

  private static void generateModifiers(StringBuilder buffer, PsiElement element) {
    String modifiers = PsiFormatUtil.formatModifiers(element, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY);

    if (modifiers.length() > 0) {
      buffer.append(modifiers);
      buffer.append(" ");
    }
  }

  private static String generatePackageInfo(PsiPackage aPackage) {
    return aPackage.getQualifiedName();
  }

  private static void generatePackageInfo(StringBuilder buffer, @NotNull PsiClass aClass) {
    PsiFile file = aClass.getContainingFile();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(aClass.getProject()).getFileIndex();
    VirtualFile vFile = file.getVirtualFile();
    if (vFile != null && (fileIndex.isInLibrarySource(vFile) || fileIndex.isInLibraryClasses(vFile))) {
      final List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(vFile);
      if (orderEntries.size() > 0) {
        final OrderEntry orderEntry = orderEntries.get(0);
        buffer.append("[").append(StringUtil.escapeXml(orderEntry.getPresentableName())).append("] ");
      }
    }
    else {
      final Module module = ModuleUtilCore.findModuleForPsiElement(file);
      if (module != null) {
        buffer.append('[').append(module.getName()).append("] ");
      }
    }

    if (file instanceof PsiJavaFile) {
      String packageName = ((PsiJavaFile)file).getPackageName();
      if (packageName.length() > 0) {
        buffer.append(packageName);
        newLine(buffer);
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static String generateClassInfo(PsiClass aClass) {
    StringBuilder buffer = new StringBuilder();

    if (aClass instanceof PsiAnonymousClass) return LangBundle.message("java.terms.anonymous.class");

    generatePackageInfo(buffer, aClass);
    generateModifiers(buffer, aClass);

    final String classString = aClass.isAnnotationType() ? "java.terms.annotation.interface"
                                                         : aClass.isInterface()
                                                           ? "java.terms.interface"
                                                           : aClass instanceof PsiTypeParameter
                                                             ? "java.terms.type.parameter"
                                                             : aClass.isEnum() ? "java.terms.enum" : "java.terms.class";
    buffer.append(LangBundle.message(classString)).append(" ");

    buffer.append(JavaDocUtil.getShortestClassName(aClass, aClass));

    if (aClass.hasTypeParameters()) {
      PsiTypeParameter[] parms = aClass.getTypeParameters();

      buffer.append("&lt;");

      for (int i = 0; i < parms.length; i++) {
        PsiTypeParameter p = parms[i];

        buffer.append(p.getName());

        PsiClassType[] refs = p.getExtendsList().getReferencedTypes();

        if (refs.length > 0) {
          buffer.append(" extends ");

          for (int j = 0; j < refs.length; j++) {
            JavaDocInfoGenerator.generateType(buffer, refs[j], aClass, false);

            if (j < refs.length - 1) {
              buffer.append(" & ");
            }
          }
        }

        if (i < parms.length - 1) {
          buffer.append(", ");
        }
      }

      buffer.append("&gt;");
    }

    PsiClassType[] refs;
    if (!aClass.isEnum() && !aClass.isAnnotationType()) {
      PsiReferenceList extendsList = aClass.getExtendsList();
      refs = extendsList == null ? PsiClassType.EMPTY_ARRAY : extendsList.getReferencedTypes();
      if (refs.length > 0 || !aClass.isInterface() && !CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) {
        buffer.append(" extends ");
        if (refs.length == 0) {
          buffer.append("Object");
        }
        else {
          for (int i = 0; i < refs.length; i++) {
            JavaDocInfoGenerator.generateType(buffer, refs[i], aClass, false);

            if (i < refs.length - 1) {
              buffer.append(", ");
            }
          }
        }
      }
    }

    refs = aClass.getImplementsListTypes();
    if (refs.length > 0) {
      newLine(buffer);
      buffer.append("implements ");
      for (int i = 0; i < refs.length; i++) {
        JavaDocInfoGenerator.generateType(buffer, refs[i], aClass, false);

        if (i < refs.length - 1) {
          buffer.append(", ");
        }
      }
    }

    return buffer.toString();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static String generateMethodInfo(PsiMethod method, PsiSubstitutor substitutor) {
    StringBuilder buffer = new StringBuilder();

    PsiClass parentClass = method.getContainingClass();

    if (parentClass != null) {
      if (method.isConstructor() && !(parentClass instanceof PsiAnonymousClass)) {
        generatePackageInfo(buffer, parentClass);
      }

      buffer.append(JavaDocUtil.getShortestClassName(parentClass, method));
      newLine(buffer);
    }

    generateModifiers(buffer, method);

    PsiTypeParameter[] params = method.getTypeParameters();

    if (params.length > 0) {
      buffer.append("&lt;");
      for (int i = 0; i < params.length; i++) {
        PsiTypeParameter param = params[i];

        buffer.append(param.getName());

        PsiClassType[] extendees = param.getExtendsList().getReferencedTypes();

        if (extendees.length > 0) {
          buffer.append(" extends ");

          for (int j = 0; j < extendees.length; j++) {
            JavaDocInfoGenerator.generateType(buffer, extendees[j], method, false);

            if (j < extendees.length - 1) {
              buffer.append(" & ");
            }
          }
        }

        if (i < params.length - 1) {
          buffer.append(", ");
        }
      }
      buffer.append("&gt; ");
    }

    if (method.getReturnType() != null) {
      JavaDocInfoGenerator.generateType(buffer, substitutor.substitute(method.getReturnType()), method, false);
      buffer.append(" ");
    }

    buffer.append(method.getName());

    buffer.append(" (");
    PsiParameter[] parms = method.getParameterList().getParameters();
    for (int i = 0; i < parms.length; i++) {
      PsiParameter parm = parms[i];
      JavaDocInfoGenerator.generateType(buffer, substitutor.substitute(parm.getType()), method, false);
      buffer.append(" ");
      if (parm.getName() != null) {
        buffer.append(parm.getName());
      }
      if (i < parms.length - 1) {
        buffer.append(", ");
      }
    }

    buffer.append(")");

    PsiClassType[] refs = method.getThrowsList().getReferencedTypes();
    if (refs.length > 0) {
      newLine(buffer);
      buffer.append(" throws ");
      for (int i = 0; i < refs.length; i++) {
        PsiClass throwsClass = refs[i].resolve();

        if (throwsClass != null) {
          buffer.append(JavaDocUtil.getShortestClassName(throwsClass, method));
        }
        else {
          buffer.append(refs[i].getPresentableText());
        }

        if (i < refs.length - 1) {
          buffer.append(", ");
        }
      }
    }

    return buffer.toString();
  }

  private static String generateFieldInfo(PsiField field, PsiSubstitutor substitutor) {
    StringBuilder buffer = new StringBuilder();
    PsiClass parentClass = field.getContainingClass();

    if (parentClass != null && !(parentClass instanceof PsiAnonymousClass)) {
      buffer.append(JavaDocUtil.getShortestClassName(parentClass, field));
      newLine(buffer);
    }

    generateModifiers(buffer, field);

    JavaDocInfoGenerator.generateType(buffer, substitutor.substitute(field.getType()), field, false);
    buffer.append(" ");
    buffer.append(field.getName());

    generateInitializer(buffer, field);

    JavaDocInfoGenerator.enumConstantOrdinal(buffer, field, parentClass, "\n");
    return buffer.toString();
  }

  private static String generateVariableInfo(PsiVariable variable) {
    StringBuilder buffer = new StringBuilder();

    generateModifiers(buffer, variable);

    JavaDocInfoGenerator.generateType(buffer, variable.getType(), variable, false);

    buffer.append(" ");

    buffer.append(variable.getName());
    generateInitializer(buffer, variable);

    return buffer.toString();
  }

  @Override
  public PsiComment findExistingDocComment(final PsiComment comment) {
    if (comment instanceof PsiDocComment) {
      final PsiDocCommentOwner owner = ((PsiDocComment)comment).getOwner();
      if (owner != null) {
        return owner.getDocComment();
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Pair<PsiElement, PsiComment> parseContext(@NotNull PsiElement startPoint) {
    for (PsiElement e = startPoint; e != null; e = e.getParent()) {
      if (e instanceof PsiDocCommentOwner) {
        return Pair.<PsiElement, PsiComment>create(e, ((PsiDocCommentOwner)e).getDocComment());
      }
    }
    return null;
  }

  @Override
  public String generateDocumentationContentStub(PsiComment _comment) {
    final PsiDocCommentOwner commentOwner = ((PsiDocComment)_comment).getOwner();
    final Project project = commentOwner.getProject();
    final StringBuilder builder = new StringBuilder();
    final CodeDocumentationAwareCommenter commenter = (CodeDocumentationAwareCommenter)LanguageCommenters.INSTANCE
      .forLanguage(commentOwner.getLanguage());
    if (commentOwner instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)commentOwner;
      final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
      final Map<String, String> param2Description = new HashMap<String, String>();
      final PsiMethod[] superMethods = psiMethod.findSuperMethods();
      for (PsiMethod superMethod : superMethods) {
        final PsiDocComment comment = superMethod.getDocComment();
        if (comment != null) {
          final PsiDocTag[] params = comment.findTagsByName("param");
          for (PsiDocTag param : params) {
            final PsiElement[] dataElements = param.getDataElements();
            if (dataElements != null) {
              String paramName = null;
              for (PsiElement dataElement : dataElements) {
                if (dataElement instanceof PsiDocParamRef) {
                  paramName = dataElement.getReference().getCanonicalText();
                  break;
                }
              }
              if (paramName != null) {
                param2Description.put(paramName, param.getText());
              }
            }
          }
        }
      }
      for (PsiParameter parameter : parameters) {
        String description = param2Description.get(parameter.getName());
        if (description != null) {
          builder.append(CodeDocumentationUtil.createDocCommentLine("", project, commenter));
          if (description.indexOf('\n') > -1) description = description.substring(0, description.lastIndexOf('\n'));
          builder.append(description);
        }
        else {
          builder.append(CodeDocumentationUtil.createDocCommentLine(PARAM_TAG, project, commenter));
          builder.append(parameter.getName());
        }
        builder.append(LINE_SEPARATOR);
      }

      final PsiTypeParameterList typeParameterList = psiMethod.getTypeParameterList();
      if (typeParameterList != null) {
        createTypeParamsListComment(builder, project, commenter, typeParameterList);
      }
      if (psiMethod.getReturnType() != null && psiMethod.getReturnType() != PsiType.VOID) {
        builder.append(CodeDocumentationUtil.createDocCommentLine(RETURN_TAG, project, commenter));
        builder.append(LINE_SEPARATOR);
      }

      final PsiJavaCodeReferenceElement[] references = psiMethod.getThrowsList().getReferenceElements();
      for (PsiJavaCodeReferenceElement reference : references) {
        builder.append(CodeDocumentationUtil.createDocCommentLine(THROWS_TAG, project, commenter));
        builder.append(reference.getText());
        builder.append(LINE_SEPARATOR);
      }
    }
    else if (commentOwner instanceof PsiClass) {
      final PsiTypeParameterList typeParameterList = ((PsiClass)commentOwner).getTypeParameterList();
      if (typeParameterList != null) {
        createTypeParamsListComment(builder, project, commenter, typeParameterList);
      }
    }
    return builder.length() > 0 ? builder.toString() : null;
  }

  private static void createTypeParamsListComment(final StringBuilder buffer,
                                                  final Project project,
                                                  final CodeDocumentationAwareCommenter commenter,
                                                  final PsiTypeParameterList typeParameterList) {
    final PsiTypeParameter[] typeParameters = typeParameterList.getTypeParameters();
    for (PsiTypeParameter typeParameter : typeParameters) {
      buffer.append(CodeDocumentationUtil.createDocCommentLine(PARAM_TAG, project, commenter));
      buffer.append("<").append(typeParameter.getName()).append(">");
      buffer.append(LINE_SEPARATOR);
    }
  }

  @Override
  public String generateDoc(final PsiElement element, final PsiElement originalElement) {
    if (element instanceof PsiMethodCallExpression) {
      return getMethodCandidateInfo((PsiMethodCallExpression)element);
    }


    //external documentation finder
    return generateExternalJavadoc(element);
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(final PsiManager psiManager, final Object object, final PsiElement element) {
    return null;
  }

  @Nullable
  public static String generateExternalJavadoc(final PsiElement element) {
    final JavaDocInfoGenerator javaDocInfoGenerator = new JavaDocInfoGenerator(element.getProject(), element);
    final List<String> docURLs = getExternalJavaDocUrl(element);
    return JavaDocExternalFilter.filterInternalDocInfo(javaDocInfoGenerator.generateDocInfo(docURLs));
  }


  @Nullable
  private static String fetchExternalJavadoc(final PsiElement element, String fromUrl, JavaDocExternalFilter filter) {
    try {
      String externalDoc = filter.getExternalDocInfoForElement(fromUrl, element);
      if (externalDoc != null && externalDoc.length() > 0) {
        return externalDoc;
      }
    }
    catch (Exception e) {
      //try to generate some javadoc
    }
    return null;
  }

  private static String getMethodCandidateInfo(PsiMethodCallExpression expr) {
    final PsiResolveHelper rh = JavaPsiFacade.getInstance(expr.getProject()).getResolveHelper();
    final CandidateInfo[] candidates = rh.getReferencedMethodCandidates(expr, true);
    final String text = expr.getText();
    if (candidates.length > 0) {
      @NonNls final StringBuilder sb = new StringBuilder();

      for (final CandidateInfo candidate : candidates) {
        final PsiElement element = candidate.getElement();

        if (!(element instanceof PsiMethod)) {
          continue;
        }

        final String str = PsiFormatUtil.formatMethod((PsiMethod)element, candidate.getSubstitutor(),
                                                      PsiFormatUtilBase.SHOW_NAME |
                                                      PsiFormatUtilBase.SHOW_TYPE |
                                                      PsiFormatUtilBase.SHOW_PARAMETERS,
                                                      PsiFormatUtilBase.SHOW_TYPE);
        createElementLink(sb, element, StringUtil.escapeXml(str));
      }

      return CodeInsightBundle.message("javadoc.candidates", text, sb);
    }

    return CodeInsightBundle.message("javadoc.candidates.not.found", text);
  }

  private static void createElementLink(@NonNls final StringBuilder sb, final PsiElement element, final String str) {
    sb.append("&nbsp;&nbsp;<a href=\"" + DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL);
    sb.append(JavaDocUtil.getReferenceText(element.getProject(), element));
    sb.append("\">");
    sb.append(str);
    sb.append("</a>");
    sb.append("<br>");
  }

  @Nullable
  public static List<String> getExternalJavaDocUrl(final PsiElement element) {
    List<String> urls = null;

    if (element instanceof PsiClass) {
      urls = findUrlForClass((PsiClass)element);
    }
    else if (element instanceof PsiField) {
      PsiField field = (PsiField)element;
      PsiClass aClass = field.getContainingClass();
      if (aClass != null) {
        urls = findUrlForClass(aClass);
        if (urls != null) {
          for (int i = 0; i < urls.size(); i++) {
            urls.set(i, urls.get(i) + "#" + field.getName());
          }
        }
      }
    }
    else if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        List<String> classUrls = findUrlForClass(aClass);
        if (classUrls != null) {
          urls = ContainerUtil.newSmartList();

          String rawSignature = formatMethodSignature(method, true);
          for (String classUrl : classUrls) {
            urls.add(classUrl + "#" + rawSignature);
          }

          String signature = formatMethodSignature(method, false);
          if (Comparing.compare(rawSignature, signature) != 0) {
            for (String classUrl : classUrls) {
              urls.add(classUrl + "#" + signature);
            }
          }
        }
      }
    }
    else if (element instanceof PsiPackage) {
      urls = findUrlForPackage((PsiPackage)element);
    }
    else if (element instanceof PsiDirectory) {
      PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
      if (aPackage != null) {
        urls = findUrlForPackage(aPackage);
      }
    }

    if (urls == null || urls.isEmpty()) {
      return null;
    }
    else {
      for (int i = 0; i < urls.size(); i++) {
        urls.set(i, FileUtil.toSystemIndependentName(urls.get(i)));
      }
      return urls;
    }
  }

  private static String formatMethodSignature(PsiMethod method, boolean raw) {
    int options = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS;
    int parameterOptions = PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_FQ_CLASS_NAMES;
    if (raw) {
      options |= PsiFormatUtilBase.SHOW_RAW_NON_TOP_TYPE;
      parameterOptions |= PsiFormatUtilBase.SHOW_RAW_NON_TOP_TYPE;
    }

    String signature = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, options, parameterOptions, 999);

    if (PsiUtil.isLanguageLevel8OrHigher(method)) {
      signature = signature.replaceAll("\\(|\\)|, ", "-").replaceAll("\\[\\]", ":A");
    }

    return signature;
  }

  @Nullable
  public static List<String> findUrlForClass(PsiClass aClass) {
    String qName = aClass.getQualifiedName();
    if (qName == null) return null;

    PsiFile file = aClass.getContainingFile();
    if (!(file instanceof PsiJavaFile)) return null;

    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;

    String packageName = ((PsiJavaFile)file).getPackageName();
    String relPath;
    if (packageName.length() > 0) {
      relPath = packageName.replace('.', '/') + '/' + qName.substring(packageName.length() + 1) + HTML_EXTENSION;
    }
    else {
      relPath = qName + HTML_EXTENSION;
    }

    return findUrlForVirtualFile(file.getProject(), virtualFile, relPath);
  }

  @Nullable
  public static List<String> findUrlForVirtualFile(final Project project, final VirtualFile virtualFile, final String relPath) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Module module = fileIndex.getModuleForFile(virtualFile);
    if (module == null) {
      final VirtualFileSystem fs = virtualFile.getFileSystem();
      if (fs instanceof JarFileSystem) {
        final VirtualFile jar = ((JarFileSystem)fs).getVirtualFileForJar(virtualFile);
        if (jar != null) {
          module = fileIndex.getModuleForFile(jar);
        }
      }
    }
    if (module != null) {
      String[] javadocPaths = JavaModuleExternalPaths.getInstance(module).getJavadocUrls();
      final List<String> httpRoots = PlatformDocumentationUtil.getHttpRoots(javadocPaths, relPath);
      // if found nothing and the file is from library classes, fall back to order entries
      if (httpRoots != null || !fileIndex.isInLibraryClasses(virtualFile)) {
        return httpRoots;
      }
    }

    final List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(virtualFile);
    for (OrderEntry orderEntry : orderEntries) {
      final String[] files = JavadocOrderRootType.getUrls(orderEntry);
      final List<String> httpRoot = PlatformDocumentationUtil.getHttpRoots(files, relPath);
      if (httpRoot != null) return httpRoot;
    }
    return null;
  }

  @Nullable
  public static List<String> findUrlForPackage(PsiPackage aPackage) {
    String qName = aPackage.getQualifiedName();
    qName = qName.replace('.', '/') + '/' + PACKAGE_SUMMARY_FILE;
    for (PsiDirectory directory : aPackage.getDirectories()) {
      List<String> url = findUrlForVirtualFile(aPackage.getProject(), directory.getVirtualFile(), qName);
      if (url != null) {
        return url;
      }
    }
    return null;
  }

  @Override
  public PsiElement getDocumentationElementForLink(final PsiManager psiManager, final String link, final PsiElement context) {
    return JavaDocUtil.findReferenceTarget(psiManager, link, context);
  }

  @Override
  public String fetchExternalDocumentation(final Project project, PsiElement element, final List<String> docUrls) {
    return fetchExternalJavadoc(element, project, docUrls);
  }

  @Override
  public boolean hasDocumentationFor(PsiElement element, PsiElement originalElement) {
    return CompositeDocumentationProvider.hasUrlsFor(this, element, originalElement);
  }

  @Override
  public boolean canPromptToConfigureDocumentation(PsiElement element) {
    return false;
  }

  @Override
  public void promptToConfigureDocumentation(PsiElement element) {
  }

  public static String fetchExternalJavadoc(PsiElement element, final Project project, final List<String> docURLs) {
    final JavaDocExternalFilter docFilter = new JavaDocExternalFilter(project);

    return fetchExternalJavadoc(element, docURLs, docFilter);
  }

  public static String fetchExternalJavadoc(PsiElement element, List<String> docURLs, @NotNull JavaDocExternalFilter docFilter) {
    if (docURLs != null) {
      for (String docURL : docURLs) {
        try {
          final String javadoc = fetchExternalJavadoc(element, docURL, docFilter);
          if (javadoc != null) return javadoc;
        }
        catch (IndexNotReadyException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.info(e); //connection problems should be ignored
        }
      }
    }
    return null;
  }
}
