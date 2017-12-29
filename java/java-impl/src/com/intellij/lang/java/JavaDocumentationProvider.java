// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.completion.CompletionMemory;
import com.intellij.codeInsight.documentation.DocumentationManagerProtocol;
import com.intellij.codeInsight.documentation.PlatformDocumentationUtil;
import com.intellij.codeInsight.editorActions.CodeDocumentationUtil;
import com.intellij.codeInsight.javadoc.JavaDocExternalFilter;
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.codeInsight.javadoc.JavaDocInfoGeneratorFactory;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.ide.util.PackageUtil;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.LangBundle;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.documentation.CodeDocumentationProvider;
import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.DocumentationProviderEx;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.beanProperties.BeanPropertyElement;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.intellij.util.Url;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.builtInWebServer.BuiltInWebBrowserUrlProviderKt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
public class JavaDocumentationProvider extends DocumentationProviderEx implements CodeDocumentationProvider, ExternalDocumentationProvider {
  private static final Logger LOG = Logger.getInstance(JavaDocumentationProvider.class);

  private static final String LINE_SEPARATOR = "\n";
  private static final String PARAM_TAG = "@param";
  private static final String RETURN_TAG = "@return";
  private static final String THROWS_TAG = "@throws";

  public static final String HTML_EXTENSION = ".html";
  public static final String PACKAGE_SUMMARY_FILE = "package-summary.html";

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
      return generateMethodInfo(((BeanPropertyElement)element).getMethod(), PsiSubstitutor.EMPTY);
    }
    else if (element instanceof PsiJavaModule) {
      return generateModuleInfo((PsiJavaModule)element);
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

  private static void generateModifiers(StringBuilder buffer, PsiModifierListOwner element) {
    String modifiers = PsiFormatUtil.formatModifiers(element, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY);
    if (modifiers.length() > 0) {
      buffer.append(modifiers);
      buffer.append(" ");
    }
  }

  private static String generatePackageInfo(PsiPackage aPackage) {
    return aPackage.getQualifiedName();
  }

  private static void generateOrderEntryAndPackageInfo(StringBuilder buffer, @NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();

    if (file != null) {
      generateOrderEntryInfo(buffer, file.getVirtualFile(), element.getProject());
    }

    if (file instanceof PsiJavaFile) {
      String packageName = ((PsiJavaFile)file).getPackageName();
      if (packageName.length() > 0) {
        buffer.append(packageName);
        newLine(buffer);
      }
    }
  }

  private static void generateOrderEntryInfo(StringBuilder buffer, VirtualFile file, Project project) {
    if (file != null) {
      ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
      if (index.isInLibrarySource(file) || index.isInLibraryClasses(file)) {
        index.getOrderEntriesForFile(file).stream()
          .filter(LibraryOrSdkOrderEntry.class::isInstance).findFirst()
          .ifPresent(entry -> buffer.append('[').append(StringUtil.escapeXml(entry.getPresentableName())).append("] "));
      }
      else {
        Module module = index.getModuleForFile(file);
        if (module != null) {
          buffer.append('[').append(module.getName()).append("] ");
        }
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static String generateClassInfo(PsiClass aClass) {
    StringBuilder buffer = new StringBuilder();

    if (aClass instanceof PsiAnonymousClass) return LangBundle.message("java.terms.anonymous.class");

    generateOrderEntryAndPackageInfo(buffer, aClass);
    generateModifiers(buffer, aClass);

    final String classString = aClass.isAnnotationType() ? "java.terms.annotation.interface"
                                                         : aClass.isInterface()
                                                           ? "java.terms.interface"
                                                           : aClass instanceof PsiTypeParameter
                                                             ? "java.terms.type.parameter"
                                                             : aClass.isEnum() ? "java.terms.enum" : "java.terms.class";
    buffer.append(LangBundle.message(classString)).append(" ");

    buffer.append(JavaDocUtil.getShortestClassName(aClass, aClass));

    generateTypeParameters(aClass, buffer);

    if (!aClass.isEnum() && !aClass.isAnnotationType()) {
      PsiReferenceList extendsList = aClass.getExtendsList();
      writeExtends(aClass, buffer, extendsList == null ? PsiClassType.EMPTY_ARRAY : extendsList.getReferencedTypes());
    }

    writeImplements(aClass, buffer, aClass.getImplementsListTypes());

    return buffer.toString();
  }

  public static void writeImplements(PsiClass aClass, StringBuilder buffer, PsiClassType[] refs) {
    if (refs.length > 0) {
      newLine(buffer);
      buffer.append("implements ");
      writeTypeRefs(aClass, buffer, refs);
    }
  }

  public static void writeExtends(PsiClass aClass, StringBuilder buffer, PsiClassType[] refs) {
    if (refs.length > 0 || !aClass.isInterface() && !CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) {
      buffer.append(" extends ");
      if (refs.length == 0) {
        buffer.append("Object");
      }
      else {
        writeTypeRefs(aClass, buffer, refs);
      }
    }
  }

  private static void writeTypeRefs(PsiClass aClass, StringBuilder buffer, PsiClassType[] refs) {
    for (int i = 0; i < refs.length; i++) {
      JavaDocInfoGenerator.generateType(buffer, refs[i], aClass, false, true);

      if (i < refs.length - 1) {
        buffer.append(", ");
      }
    }
  }

  public static void generateTypeParameters(PsiTypeParameterListOwner typeParameterOwner, StringBuilder buffer) {
    if (typeParameterOwner.hasTypeParameters()) {
      PsiTypeParameter[] params = typeParameterOwner.getTypeParameters();

      buffer.append("&lt;");

      for (int i = 0; i < params.length; i++) {
        PsiTypeParameter p = params[i];

        buffer.append(p.getName());
        PsiClassType[] refs = p.getExtendsList().getReferencedTypes();

        if (refs.length > 0) {
          buffer.append(" extends ");

          for (int j = 0; j < refs.length; j++) {
            JavaDocInfoGenerator.generateType(buffer, refs[j], typeParameterOwner, false, true);

            if (j < refs.length - 1) {
              buffer.append(" & ");
            }
          }
        }

        if (i < params.length - 1) {
          buffer.append(", ");
        }
      }

      buffer.append("&gt;");
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static String generateMethodInfo(PsiMethod method, PsiSubstitutor substitutor) {
    StringBuilder buffer = new StringBuilder();

    PsiClass parentClass = method.getContainingClass();

    if (parentClass != null && !(parentClass instanceof PsiAnonymousClass)) {
      if (method.isConstructor()) {
        generateOrderEntryAndPackageInfo(buffer, parentClass);
      }

      buffer.append(JavaDocUtil.getShortestClassName(parentClass, method));
      newLine(buffer);
    }

    generateModifiers(buffer, method);

    generateTypeParameters(method, buffer);

    if (method.getReturnType() != null) {
      JavaDocInfoGenerator.generateType(buffer, substitutor.substitute(method.getReturnType()), method, false, true);
      buffer.append(" ");
    }

    buffer.append(method.getName());

    buffer.append("(");
    PsiParameter[] params = method.getParameterList().getParameters();
    for (int i = 0; i < params.length; i++) {
      PsiParameter param = params[i];
      JavaDocInfoGenerator.generateType(buffer, substitutor.substitute(param.getType()), method, false, true);
      buffer.append(" ");
      if (param.getName() != null) {
        buffer.append(param.getName());
      }
      if (i < params.length - 1) {
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

    JavaDocInfoGenerator.generateType(buffer, substitutor.substitute(field.getType()), field, false, true);
    buffer.append(" ");
    buffer.append(field.getName());

    generateInitializer(buffer, field);

    JavaDocInfoGenerator.enumConstantOrdinal(buffer, field, parentClass, "\n");
    return buffer.toString();
  }

  private static String generateVariableInfo(PsiVariable variable) {
    StringBuilder buffer = new StringBuilder();

    generateModifiers(buffer, variable);

    JavaDocInfoGenerator.generateType(buffer, variable.getType(), variable, false, true);

    buffer.append(" ");

    buffer.append(variable.getName());
    generateInitializer(buffer, variable);

    return buffer.toString();
  }

  private static String generateModuleInfo(PsiJavaModule module) {
    StringBuilder sb = new StringBuilder();

    VirtualFile file = PsiImplUtil.getModuleVirtualFile(module);
    generateOrderEntryInfo(sb, file, module.getProject());

    sb.append(LangBundle.message("java.terms.module")).append(' ').append(module.getName());

    return sb.toString();
  }

  @Override
  public PsiComment findExistingDocComment(final PsiComment comment) {
    if (comment instanceof PsiDocComment) {
      final PsiJavaDocumentedElement owner = ((PsiDocComment)comment).getOwner();
      if (owner != null) {
        return owner.getDocComment();
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Pair<PsiElement, PsiComment> parseContext(@NotNull PsiElement startPoint) {
    PsiElement current = startPoint;
    while (current != null) {
      if (current instanceof PsiJavaDocumentedElement && !(current instanceof PsiTypeParameter) && !(current instanceof PsiAnonymousClass)) {
        PsiDocComment comment = ((PsiJavaDocumentedElement)current).getDocComment();
        return Pair.create(current instanceof PsiField ? ((PsiField)current).getModifierList() : current, comment);
      }
      else if (PackageUtil.isPackageInfoFile(current)) {
        return Pair.create(current, getPackageInfoComment(current));
      }
      current = current.getParent();
    }
    return null;
  }

  @Override
  public String generateDocumentationContentStub(PsiComment _comment) {
    final PsiJavaDocumentedElement commentOwner = ((PsiDocComment)_comment).getOwner();
    final Project project = _comment.getProject();
    final StringBuilder builder = new StringBuilder();
    final CodeDocumentationAwareCommenter commenter =
      (CodeDocumentationAwareCommenter)LanguageCommenters.INSTANCE.forLanguage(_comment.getLanguage());
    if (commentOwner instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)commentOwner;
      generateParametersTakingDocFromSuperMethods(project, builder, commenter, psiMethod);

      final PsiTypeParameterList typeParameterList = psiMethod.getTypeParameterList();
      if (typeParameterList != null) {
        createTypeParamsListComment(builder, project, commenter, typeParameterList);
      }
      if (psiMethod.getReturnType() != null && !PsiType.VOID.equals(psiMethod.getReturnType())) {
        builder.append(CodeDocumentationUtil.createDocCommentLine(RETURN_TAG, _comment.getContainingFile(), commenter));
        builder.append(LINE_SEPARATOR);
      }

      final PsiJavaCodeReferenceElement[] references = psiMethod.getThrowsList().getReferenceElements();
      for (PsiJavaCodeReferenceElement reference : references) {
        builder.append(CodeDocumentationUtil.createDocCommentLine(THROWS_TAG, _comment.getContainingFile(), commenter));
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

  public static void generateParametersTakingDocFromSuperMethods(Project project,
                                                                 StringBuilder builder,
                                                                 CodeDocumentationAwareCommenter commenter, PsiMethod psiMethod) {
    final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
    final Map<String, String> param2Description = new HashMap<>();
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
                //noinspection ConstantConditions
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
        builder.append(CodeDocumentationUtil.createDocCommentLine("", psiMethod.getContainingFile(), commenter));
        if (description.indexOf('\n') > -1) description = description.substring(0, description.lastIndexOf('\n'));
        builder.append(description);
      }
      else {
        builder.append(CodeDocumentationUtil.createDocCommentLine(PARAM_TAG, psiMethod.getContainingFile(), commenter));
        builder.append(parameter.getName());
      }
      builder.append(LINE_SEPARATOR);
    }
  }

  public static void createTypeParamsListComment(final StringBuilder buffer,
                                                  final Project project,
                                                  final CodeDocumentationAwareCommenter commenter,
                                                  final PsiTypeParameterList typeParameterList) {
    final PsiTypeParameter[] typeParameters = typeParameterList.getTypeParameters();
    for (PsiTypeParameter typeParameter : typeParameters) {
      buffer.append(CodeDocumentationUtil.createDocCommentLine(PARAM_TAG, typeParameterList.getContainingFile(), commenter));
      buffer.append("<").append(typeParameter.getName()).append(">");
      buffer.append(LINE_SEPARATOR);
    }
  }

  @Override
  public String generateDoc(PsiElement element, PsiElement originalElement) {
    // for new Class(<caret>) or methodCall(<caret>) proceed from method call or new expression
    // same for new Cl<caret>ass() or method<caret>Call()
    if (element instanceof PsiExpressionList ||
        element instanceof PsiReferenceExpression && element.getParent() instanceof PsiMethodCallExpression) {
      element = element.getParent();
      originalElement = null;
    }
    if (element instanceof PsiMethodCallExpression) {
      PsiMethod method = CompletionMemory.getChosenMethod((PsiMethodCallExpression)element);
      if (method == null) return getMethodCandidateInfo((PsiMethodCallExpression)element);
      else element = method;
    }

    // Try hard for documentation of incomplete new Class instantiation
    PsiElement elt = originalElement != null && !(originalElement instanceof PsiPackage) ? PsiTreeUtil.prevLeaf(originalElement): element;
    if (elt instanceof PsiErrorElement) elt = elt.getPrevSibling();
    else if (elt != null && !(elt instanceof PsiNewExpression)) {
      elt = elt.getParent();
    }
    if (elt instanceof PsiNewExpression) {
      PsiClass targetClass = null;

      if (element instanceof PsiJavaCodeReferenceElement) {     // new Class<caret>
        PsiElement resolve = ((PsiJavaCodeReferenceElement)element).resolve();
        if (resolve instanceof PsiClass) targetClass = (PsiClass)resolve;
      } else if (element instanceof PsiClass) { //Class in completion
        targetClass = (PsiClass)element;
      } else if (element instanceof PsiNewExpression) { // new Class(<caret>)
        PsiJavaCodeReferenceElement reference = ((PsiNewExpression)element).getClassReference();
        if (reference != null) {
          PsiElement resolve = reference.resolve();
          if (resolve instanceof PsiClass) targetClass = (PsiClass)resolve;
        }
      }

      if (targetClass != null) {
        PsiMethod[] constructors = targetClass.getConstructors();
        if (constructors.length > 0) {
          if (constructors.length == 1) return generateDoc(constructors[0], originalElement);
          final StringBuilder sb = new StringBuilder();

          for(PsiMethod constructor:constructors) {
            final String str = PsiFormatUtil.formatMethod(constructor, PsiSubstitutor.EMPTY,
                                                          PsiFormatUtilBase.SHOW_NAME |
                                                          PsiFormatUtilBase.SHOW_TYPE |
                                                          PsiFormatUtilBase.SHOW_PARAMETERS,
                                                          PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_NAME);
            createElementLink(sb, constructor, StringUtil.escapeXml(str));
          }

          return CodeInsightBundle.message("javadoc.constructor.candidates", targetClass.getName(), sb);
        }
      }
    }

    //external documentation finder
    return generateExternalJavadoc(element);
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(final PsiManager psiManager, final Object object, final PsiElement element) {
    return null;
  }

  @Nullable
  public static String generateExternalJavadoc(@NotNull final PsiElement element) {
    List<String> docURLs = getExternalJavaDocUrl(element);
    return generateExternalJavadoc(element, docURLs);
  }

  @Nullable
  public static String generateExternalJavadoc(@NotNull final PsiElement element, @Nullable List<String> docURLs) {
    final JavaDocInfoGenerator javaDocInfoGenerator = JavaDocInfoGeneratorFactory.create(element.getProject(), element);
    return generateExternalJavadoc(javaDocInfoGenerator, docURLs);
  }

  @Nullable
  public static String generateExternalJavadoc(@NotNull final PsiElement element, @NotNull JavaDocInfoGenerator generator) {
    final List<String> docURLs = getExternalJavaDocUrl(element);
    return generateExternalJavadoc(generator, docURLs);
  }

  @Nullable
  private static String generateExternalJavadoc(@NotNull JavaDocInfoGenerator generator, @Nullable List<String> docURLs) {
    return JavaDocExternalFilter.filterInternalDocInfo(generator.generateDocInfo(docURLs));
  }

  @Nullable
  private static String fetchExternalJavadoc(final PsiElement element, String fromUrl, @NotNull JavaDocExternalFilter filter) {
    try {
      String externalDoc = filter.getExternalDocInfoForElement(fromUrl, element);
      if (!StringUtil.isEmpty(externalDoc)) {
        return externalDoc;
      }
    }
    catch (ProcessCanceledException ignored) {}
    catch (Exception e) {
      LOG.warn(e);
    }
    return null;
  }

  private String getMethodCandidateInfo(PsiMethodCallExpression expr) {
    final PsiResolveHelper rh = JavaPsiFacade.getInstance(expr.getProject()).getResolveHelper();
    final CandidateInfo[] candidates = rh.getReferencedMethodCandidates(expr, true);
    final String text = expr.getText();
    if (candidates.length > 0) {
      if (candidates.length == 1) {
        PsiElement element = candidates[0].getElement();
        if (element instanceof PsiMethod) return generateDoc(element, null);
      }
      final StringBuilder sb = new StringBuilder();

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

  private static void createElementLink(StringBuilder sb, PsiElement element, String str) {
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

          final boolean useJava8Format = PsiUtil.isLanguageLevel8OrHigher(method);

          final Set<String> signatures = getHtmlMethodSignatures(method, useJava8Format);
          for (String signature : signatures) {
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

  public static Set<String> getHtmlMethodSignatures(PsiMethod method, boolean java8FormatFirst) {
    final Set<String> signatures = new LinkedHashSet<>();
    signatures.add(formatMethodSignature(method, true, java8FormatFirst));
    signatures.add(formatMethodSignature(method, false, java8FormatFirst));

    signatures.add(formatMethodSignature(method, true, !java8FormatFirst));
    signatures.add(formatMethodSignature(method, false, !java8FormatFirst));
    return signatures;
  }

  private static String formatMethodSignature(PsiMethod method, boolean raw, boolean java8Format) {
    int options = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS;
    int parameterOptions = PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_FQ_CLASS_NAMES;
    if (raw) {
      options |= PsiFormatUtilBase.SHOW_RAW_NON_TOP_TYPE;
      parameterOptions |= PsiFormatUtilBase.SHOW_RAW_NON_TOP_TYPE;
    }

    String signature = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, options, parameterOptions, 999);

    if (java8Format) {
      signature = signature.replaceAll("\\(|\\)|, ", "-").replaceAll("\\[\\]", ":A");
    }

    return signature;
  }

  @Nullable
  public static PsiDocComment getPackageInfoComment(@NotNull PsiElement packageInfoFile) {
    return PsiTreeUtil.getChildOfType(packageInfoFile, PsiDocComment.class);
  }

  @Nullable
  public static List<String> findUrlForClass(@NotNull PsiClass aClass) {
    String qName = aClass.getQualifiedName();
    if (qName == null) return null;

    PsiFile file = aClass.getContainingFile();
    if (!(file instanceof PsiJavaFile)) return null;

    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;

    String packageName = ((PsiJavaFile)file).getPackageName();
    String relPath;
    if (packageName.isEmpty()) {
      relPath = qName + HTML_EXTENSION;
    }
    else {
      relPath = packageName.replace('.', '/') + '/' + qName.substring(packageName.length() + 1) + HTML_EXTENSION;
    }

    return findUrlForVirtualFile(file.getProject(), virtualFile, relPath);
  }

  @Nullable
  public static List<String> findUrlForVirtualFile(@NotNull Project project, @NotNull VirtualFile virtualFile, @NotNull String relPath) {
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

    for (OrderEntry orderEntry : fileIndex.getOrderEntriesForFile(virtualFile)) {
      for (VirtualFile root : orderEntry.getFiles(JavadocOrderRootType.getInstance())) {
        if (root.getFileSystem() == JarFileSystem.getInstance()) {
          VirtualFile file = root.findFileByRelativePath(relPath);
          List<Url> urls = file == null ? null : BuiltInWebBrowserUrlProviderKt.getBuiltInServerUrls(file, project, null);
          if (!ContainerUtil.isEmpty(urls)) {
            List<String> result = new SmartList<>();
            for (Url url : urls) {
              result.add(url.toExternalForm());
            }
            return result;
          }
        }
      }

      List<String> httpRoot = PlatformDocumentationUtil.getHttpRoots(JavadocOrderRootType.getUrls(orderEntry), relPath);
      if (httpRoot != null) {
        return httpRoot;
      }
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
  public String fetchExternalDocumentation(Project project, PsiElement element, List<String> docUrls) {
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

  @Nullable
  @Override
  public PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file, @Nullable PsiElement contextElement) {
    PsiDocComment docComment = PsiTreeUtil.getParentOfType(contextElement, PsiDocComment.class, false);
    if (docComment != null && JavaDocUtil.isInsidePackageInfo(docComment)) {
      PsiDirectory directory = file.getContainingDirectory();
      if (directory != null) {
        return JavaDirectoryService.getInstance().getPackage(directory);
      }
    }
    return null;
  }

  public static String fetchExternalJavadoc(PsiElement element, final Project project, final List<String> docURLs) {
    return fetchExternalJavadoc(element, docURLs, new JavaDocExternalFilter(project));
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