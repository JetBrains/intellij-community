/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.InferredAnnotationsManager;
import com.intellij.codeInsight.documentation.DocumentationManagerProtocol;
import com.intellij.codeInsight.documentation.DocumentationManagerUtil;
import com.intellij.javadoc.JavadocGeneratorRunProfile;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LangBundle;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaDocInfoGenerator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.javadoc.JavaDocInfoGenerator");

  @NonNls private static final Pattern ourWhitespaces = Pattern.compile("[ \\n\\r\\t]+");
  @NonNls private static final Pattern ourRelativeHtmlLinks = Pattern.compile("<A.*?HREF=\"([^\":]*)\"",
                                                                              Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  @NonNls private static final String THROWS_KEYWORD = "throws";
  @NonNls private static final String BR_TAG         = "<br>";
  @NonNls private static final String LINK_TAG       = "link";
  @NonNls private static final String LITERAL_TAG    = "literal";
  @NonNls private static final String CODE_TAG       = "code";
  @NonNls private static final String LINKPLAIN_TAG  = "linkplain";
  @NonNls private static final String INHERITDOC_TAG = "inheritDoc";
  @NonNls private static final String DOCROOT_TAG    = "docRoot";
  @NonNls private static final String VALUE_TAG      = "value";
  private static final String LT = "&lt;";
  private static final String GT = "&gt;";

  private final Project    myProject;
  private final PsiElement myElement;
  private final JavaSdkVersion mySdkVersion;

  interface InheritDocProvider<T> {
    Pair<T, InheritDocProvider<T>> getInheritDoc();

    PsiClass getElement();
  }

  private static final InheritDocProvider<PsiDocTag> ourEmptyProvider = new InheritDocProvider<PsiDocTag>() {
    @Override
    public Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> getInheritDoc() {
      return null;
    }

    @Override
    public PsiClass getElement() {
      return null;
    }
  };

  private static final InheritDocProvider<PsiElement[]> ourEmptyElementsProvider = mapProvider(ourEmptyProvider, false);

  private static InheritDocProvider<PsiElement[]> mapProvider(final InheritDocProvider<PsiDocTag> i,
                                                              final boolean dropFirst)
  {
    return new InheritDocProvider<PsiElement[]>() {
      @Override
      public Pair<PsiElement[], InheritDocProvider<PsiElement[]>> getInheritDoc() {
        Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> pair = i.getInheritDoc();

        if (pair == null) {
          return null;
        }

        PsiElement[] elements;
        PsiElement[] rawElements = pair.first.getDataElements();

        if (dropFirst && rawElements != null && rawElements.length > 0) {
          elements = new PsiElement[rawElements.length - 1];

          System.arraycopy(rawElements, 1, elements, 0, elements.length);
        }
        else {
          elements = rawElements;
        }

        return Pair.create(elements, mapProvider(pair.second, dropFirst));
      }

      @Override
      public PsiClass getElement() {
        return i.getElement();
      }
    };
  }

  interface DocTagLocator <T> {
    T find(PsiDocCommentOwner owner, PsiDocComment comment);
  }

  private static DocTagLocator<PsiDocTag> parameterLocator(final int parameterIndex) {
    return new DocTagLocator<PsiDocTag>() {
      @Override
      public PsiDocTag find(PsiDocCommentOwner owner, PsiDocComment comment) {
        if (parameterIndex < 0 || comment == null || !(owner instanceof PsiMethod)) return null;

        PsiParameter[] parameters = ((PsiMethod)owner).getParameterList().getParameters();
        if (parameterIndex >= parameters.length) return null;
        
        String name = parameters[parameterIndex].getName();
        return getParamTagByName(comment, name);
      }
    };
  }

  private static DocTagLocator<PsiDocTag> typeParameterLocator(final int parameterIndex) {
    return new DocTagLocator<PsiDocTag>() {
      @Override
      public PsiDocTag find(PsiDocCommentOwner owner, PsiDocComment comment) {
        if (parameterIndex < 0 || comment == null || !(owner instanceof PsiTypeParameterListOwner)) return null;
        
        PsiTypeParameter[] parameters = ((PsiTypeParameterListOwner)owner).getTypeParameters();
        if (parameterIndex >= parameters.length) return null;

        String rawName = parameters[parameterIndex].getName();
        if (rawName == null) return null;
        String name = "<" + rawName + ">";
        return getParamTagByName(comment, name);
      }
    };
  }

  private static PsiDocTag getParamTagByName(@NotNull PsiDocComment comment, String name) {
    PsiDocTag[] tags = comment.findTagsByName("param");
    return getTagByName(tags, name);
  }

  private static PsiDocTag getTagByName(@NotNull PsiDocTag[] tags, String name) {
    for (PsiDocTag tag : tags) {
      PsiDocTagValue value = tag.getValueElement();

      if (value != null) {
        String text = value.getText();

        if (text != null && text.equals(name)) {
          return tag;
        }
      }
    }

    return null;
  }
  
  private static DocTagLocator<PsiDocTag> exceptionLocator(final String name) {
    return new DocTagLocator<PsiDocTag>() {
      @Override
      public PsiDocTag find(PsiDocCommentOwner owner, PsiDocComment comment) {
        if (comment == null) {
          return null;
        }

        PsiDocTag[] tags = getThrowsTags(comment);

        for (PsiDocTag tag : tags) {
          PsiDocTagValue value = tag.getValueElement();

          if (value != null) {
            String text = value.getText();

            if (text != null && areWeakEqual(text, name)) {
              return tag;
            }
          }
        }

        return null;
      }
    };
  }

  public JavaDocInfoGenerator(Project project, PsiElement element) {
    myProject = project;
    myElement = element;
    
    Sdk jdk = JavadocGeneratorRunProfile.getSdk(myProject);
    mySdkVersion = jdk == null ? null : JavaSdk.getInstance().getVersion(jdk);
  }

  @Nullable
  public String generateFileInfo() {
    StringBuilder buffer = new StringBuilder();
    if (myElement instanceof PsiFile) {
      generateFileJavaDoc(buffer, (PsiFile)myElement, true); //used for Ctrl-Click
    }

    return fixupDoc(buffer);
  }

  @Nullable
  private String fixupDoc(@NotNull final StringBuilder buffer) {
    String text = buffer.toString();
    if (text.isEmpty()) {
      return null;
    }
    
    text = convertHtmlLinks(text);
    
    if (LOG.isDebugEnabled()) {
      LOG.debug("Generated JavaDoc:");
      LOG.debug(text);
    }

    text = StringUtil.replaceIgnoreCase(text, "<p/>", "<p></p>");
    return StringUtil.replace(text, "/>", ">");
  }

  private String convertHtmlLinks(String text) {
    if (myElement == null) return text; // we are resolving links in a context, without context, don't change links
    StringBuilder result = new StringBuilder();
    int prev = 0;
    Matcher matcher = ourRelativeHtmlLinks.matcher(text);
    while (matcher.find()) {
      int groupStart = matcher.start(1);
      int groupEnd = matcher.end(1);
      result.append(text, prev, groupStart);
      result.append(convertReference(text.substring(groupStart, groupEnd)));
      prev = groupEnd;
    }
    if (result.length() == 0) return text; // don't copy text over, if there are no matches
    result.append(text, prev, text.length());
    return result.toString();
  }

  protected String convertReference(@NonNls String href) {
    String reference = createReferenceForRelativeLink(href, myElement);
    return reference == null ? href : reference;
  }

  /**
   * Converts a relative link into {@link DocumentationManagerProtocol#PSI_ELEMENT_PROTOCOL PSI_ELEMENT_PROTOCOL}-type link if possible
   */
  @Nullable
  static String createReferenceForRelativeLink(@NotNull @NonNls String relativeLink, @NotNull PsiElement contextElement) {
    String fragment = null;
    int hashPosition = relativeLink.indexOf('#');
    if (hashPosition >= 0) {
      fragment = relativeLink.substring(hashPosition + 1);
      relativeLink = relativeLink.substring(0, hashPosition);
    }
    PsiElement targetElement;
    if (relativeLink.isEmpty()) {
      targetElement = (contextElement instanceof PsiField || contextElement instanceof PsiMethod) ? 
                      ((PsiMember)contextElement).getContainingClass() : contextElement;
    } 
    else {
      if (!relativeLink.toLowerCase().endsWith(".htm") && !relativeLink.toLowerCase().endsWith(".html")) {
        return null;
      }
      relativeLink = relativeLink.substring(0, relativeLink.lastIndexOf('.'));
      
      String packageName = getPackageName(contextElement);
      if (packageName == null) return null;

      Couple<String> pathWithPackage = removeParentReferences(Couple.of(relativeLink, packageName));
      if (pathWithPackage == null) return null;
      relativeLink = pathWithPackage.first;
      packageName = pathWithPackage.second;

      relativeLink = relativeLink.replace('/', '.');

      String qualifiedTargetName = packageName.isEmpty() ? relativeLink : packageName + "." + relativeLink;
      JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(contextElement.getProject());
      targetElement = "package-summary".equals(StringUtil.getShortName(qualifiedTargetName))
                      ? javaPsiFacade.findPackage(StringUtil.getPackageName(qualifiedTargetName))
                      : javaPsiFacade.findClass(qualifiedTargetName, contextElement.getResolveScope());
    }
    if (targetElement == null) return null;
    
    if (fragment != null && targetElement instanceof PsiClass) {
      if (fragment.contains("-") || fragment.contains("(")) {
        for (PsiMethod method : ((PsiClass)targetElement).getMethods()) {
          Set<String> signatures = JavaDocumentationProvider.getHtmlMethodSignatures(method, true);
          if (signatures.contains(fragment)) {
            targetElement = method;
            fragment = null;
            break;
          }
        }
      }
      else  {
        for (PsiField field : ((PsiClass)targetElement).getFields()) {
          if (fragment.equals(field.getName())) {
            targetElement = field;
            fragment = null;
            break;
          }
        }
      }
    }
    return DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL + JavaDocUtil.getReferenceText(targetElement.getProject(), targetElement) +
           (fragment == null ? "" : DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL_REF_SEPARATOR + fragment);
  }

  /**
   * Takes a pair of strings representing a relative path and a package name, and returns corresponding pair, where path is stripped of
   * leading ../ elements, and package name adjusted correspondingly. Returns <code>null</code> if there are more ../ elements than package
   * components.
   */
  @Nullable
  static Couple<String> removeParentReferences(Couple<String> pathWithContextPackage) {
    String path = pathWithContextPackage.first;
    String packageName = pathWithContextPackage.second;
    while (path.startsWith("../")) {
      if (packageName.isEmpty()) return null;
      int dotPos = packageName.lastIndexOf('.');
      packageName = dotPos < 0 ? "" : packageName.substring(0, dotPos);
      path = path.substring(3);
    }
    return Couple.of(path, packageName);
  }

  static String getPackageName(PsiElement element) {
    String packageName = null;
    if (element instanceof PsiPackage) {
      packageName = ((PsiPackage)element).getQualifiedName();
    }
    else {
      PsiFile file = element.getContainingFile();
      if (file instanceof PsiClassOwner) {
        packageName = ((PsiClassOwner)file).getPackageName();
      }
    }
    return packageName;
  }

  public boolean generateDocInfoCore (final StringBuilder buffer, final boolean generatePrologueAndEpilogue) {
    if (myElement instanceof PsiClass) {
      generateClassJavaDoc(buffer, (PsiClass)myElement, generatePrologueAndEpilogue);
    }
    else if (myElement instanceof PsiMethod) {
      generateMethodJavaDoc(buffer, (PsiMethod)myElement, generatePrologueAndEpilogue);
    } else if (myElement instanceof PsiParameter) {
      generateMethodParameterJavaDoc(buffer, (PsiParameter)myElement, generatePrologueAndEpilogue);
    }
    else if (myElement instanceof PsiField) {
      generateFieldJavaDoc(buffer, (PsiField)myElement, generatePrologueAndEpilogue);
    }
    else if (myElement instanceof PsiVariable) {
      generateVariableJavaDoc(buffer, (PsiVariable)myElement, generatePrologueAndEpilogue);
    }
    else if (myElement instanceof PsiDirectory) {
      final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)myElement);
      if (aPackage == null)
        return false;
      generatePackageJavaDoc(buffer, aPackage, generatePrologueAndEpilogue);
    }
    else if (myElement instanceof PsiPackage) {
      generatePackageJavaDoc(buffer, (PsiPackage) myElement, generatePrologueAndEpilogue);
    } else {
      return false;
    }

    return true;
  }

  public static String generateSignature(PsiElement element) {
    StringBuilder buf = new StringBuilder();
    if (element instanceof PsiClass) {
      if (generateClassSignature(buf, (PsiClass)element, false)) return null;
    } 
    else if (element instanceof PsiField) {
      generateFieldSignature(buf, (PsiField)element, false);
    }
    else if (element instanceof PsiMethod) {
      generateMethodSignature(buf, (PsiMethod)element, false, true);
    }
    return buf.toString();
  }

  @Nullable
  public String generateDocInfo(List<String> docURLs) {
    StringBuilder buffer = new StringBuilder();

    if (!generateDocInfoCore(buffer, true))
      return null;

    if (docURLs != null) {
      if (buffer.length() > 0 && elementHasSourceCode()) {
        LOG.debug("Documentation for " + myElement + " was generated from source code, it wasn't found at following URLs: ", docURLs);
      }
      else {
        if (buffer.length() == 0) {
          buffer.append("<html><body></body></html>");
        }
        String errorSection = "<p id=\"error\">Following external urls were checked:<br>&nbsp;&nbsp;&nbsp;<i>" +
                              StringUtil.join(docURLs, XmlStringUtil::escapeString, "</i><br>&nbsp;&nbsp;&nbsp;<i>") +
                              "</i><br>The documentation for this element is not found. Please add all the needed paths to API docs in " +
                              "<a href=\"open://Project Settings\">Project Settings.</a></p>";
        buffer.insert(buffer.indexOf("<body>"), errorSection);
      }
    }
    return fixupDoc(buffer);
  }

  private boolean elementHasSourceCode() {
    PsiFileSystemItem[] items;
    if (myElement instanceof PsiDirectory) {
      final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)myElement);
      if (aPackage == null) return false;
      items = aPackage.getDirectories(new EverythingGlobalScope(myProject));
    }
    else if (myElement instanceof PsiPackage) {
      items = ((PsiPackage)myElement).getDirectories(new EverythingGlobalScope(myProject));
    }
    else {
      PsiFile containingFile = myElement.getNavigationElement().getContainingFile();
      if (containingFile == null) return false;
      items = new PsiFileSystemItem[] {containingFile};
    }
    ProjectFileIndex projectFileIndex = ProjectFileIndex.SERVICE.getInstance(myProject);
    for (PsiFileSystemItem item : items) {
      VirtualFile file = item.getVirtualFile();
      if (file != null && projectFileIndex.isInSource(file)) return true;
    }
    return false;
  }

  private void generateClassJavaDoc(@NonNls StringBuilder buffer, PsiClass aClass, boolean generatePrologueAndEpilogue) {
    if (aClass instanceof PsiAnonymousClass) return;
    if (generatePrologueAndEpilogue)
      generatePrologue(buffer);

    PsiFile file = aClass.getContainingFile();
    if (file instanceof PsiJavaFile) {
      String packageName = ((PsiJavaFile)file).getPackageName();
      if (!packageName.isEmpty()) {
        buffer.append("<small><b>");
        buffer.append(packageName);
        buffer.append("</b></small>");
      }
    }

    buffer.append("<PRE>");
    if (generateClassSignature(buffer, aClass, true)) return;
    buffer.append("</PRE>");

    PsiDocComment comment = getDocComment(aClass);
    if (comment != null) {
      generateCommonSection(buffer, comment);
      generateTypeParametersSection(buffer, aClass);
    }

    if (generatePrologueAndEpilogue)
      generateEpilogue(buffer);
  }

  private static boolean generateClassSignature(StringBuilder buffer, PsiClass aClass, boolean generateLink) {
    generateAnnotations(buffer, aClass, generateLink, true, false);
    String modifiers = PsiFormatUtil.formatModifiers(aClass, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY);
    if (!modifiers.isEmpty()) {
      buffer.append(modifiers);
      buffer.append(" ");
    }
    buffer.append(LangBundle.message(aClass.isInterface() ? "java.terms.interface" : "java.terms.class"));
    buffer.append(" ");
    String refText = JavaDocUtil.getReferenceText(aClass.getProject(), aClass);
    if (refText == null) {
      buffer.setLength(0);
      return true;
    }
    String labelText = JavaDocUtil.getLabelText(aClass.getProject(), aClass.getManager(), refText, aClass);
    buffer.append("<b>");
    buffer.append(labelText);
    buffer.append("</b>");

    buffer.append(generateTypeParameters(aClass, false));

    buffer.append("\n");

    PsiClassType[] refs = aClass.getExtendsListTypes();

    String qName = aClass.getQualifiedName();

    if (refs.length > 0 || !aClass.isInterface() && (qName == null || !qName.equals(CommonClassNames.JAVA_LANG_OBJECT))) {
      buffer.append("extends ");
      if (refs.length == 0) {
        generateLink(buffer, CommonClassNames.JAVA_LANG_OBJECT, null, aClass, false);
      }
      else {
        for (int i = 0; i < refs.length; i++) {
          generateType(buffer, refs[i], aClass, generateLink);
          if (i < refs.length - 1) {
            buffer.append(",&nbsp;");
          }
        }
      }
      buffer.append("\n");
    }

    refs = aClass.getImplementsListTypes();

    if (refs.length > 0) {
      buffer.append("implements ");
      for (int i = 0; i < refs.length; i++) {
        generateType(buffer, refs[i], aClass, generateLink);
        if (i < refs.length - 1) {
          buffer.append(",&nbsp;");
        }
      }
      buffer.append("\n");
    }
    if (buffer.charAt(buffer.length() - 1) == '\n') {
      buffer.setLength(buffer.length() - 1);
    }
    return false;
  }

  private void generateTypeParametersSection(final StringBuilder buffer, final PsiClass aClass) {
    final LinkedList<ParamInfo> result = new LinkedList<>();
    final PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
    for (int i = 0; i < typeParameters.length; i++) {
      PsiTypeParameter typeParameter = typeParameters[i];
      String name = "<" + typeParameter.getName() + ">";
      final DocTagLocator<PsiDocTag> locator = typeParameterLocator(i);
      final Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> inClassComment = findInClassComment(aClass, locator);
      if (inClassComment != null) {
        result.add(new ParamInfo(name, inClassComment));
      }
      else {
        final Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> pair = findInHierarchy(aClass, locator);
        if (pair != null) {
          result.add(new ParamInfo(name, pair));
        }
      }
    }
    generateParametersSection(buffer, CodeInsightBundle.message("javadoc.type.parameters"), result);
  }

  @Nullable
  private static Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> findInHierarchy(PsiClass psiClass, final DocTagLocator<PsiDocTag> locator) {
    for (final PsiClass superClass : psiClass.getSupers()) {
      final Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> pair = findInClassComment(superClass, locator);
      if (pair != null) return pair;
    }
    for (PsiClass superInterface : psiClass.getInterfaces()) {
      final Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> pair = findInClassComment(superInterface, locator);
      if (pair != null) return pair;
    }
    return null;
  }

  private static Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> findInClassComment(final PsiClass psiClass, final DocTagLocator<PsiDocTag> locator) {
    final PsiDocTag tag = locator.find(psiClass, getDocComment(psiClass));
    if (tag != null) {
      return new Pair<>(tag, new InheritDocProvider<PsiDocTag>() {
        @Override
        public Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> getInheritDoc() {
          return findInHierarchy(psiClass, locator);
        }

        @Override
        public PsiClass getElement() {
          return psiClass;
        }
      });
    }
    return null;
  }

  @Nullable
  private static PsiDocComment getDocComment(final PsiDocCommentOwner docOwner) {
    PsiElement navElement = docOwner.getNavigationElement();
    if (!(navElement instanceof PsiDocCommentOwner)) {
      LOG.info("Wrong navElement: " + navElement + "; original = " + docOwner + " of class " + docOwner.getClass());
      return null;
    }
    PsiDocComment comment = ((PsiDocCommentOwner)navElement).getDocComment();
    if (comment == null) { //check for non-normalized fields
      final PsiModifierList modifierList = docOwner.getModifierList();
      if (modifierList != null) {
        final PsiElement parent = modifierList.getParent();
        if (parent instanceof PsiDocCommentOwner && parent.getNavigationElement() instanceof PsiDocCommentOwner) {
          return ((PsiDocCommentOwner)parent.getNavigationElement()).getDocComment();
        }
      }
    }
    return comment;
  }

  private void generateFieldJavaDoc(@NonNls StringBuilder buffer, PsiField field, boolean generatePrologueAndEpilogue) {
    if (generatePrologueAndEpilogue)
      generatePrologue(buffer);

    generateLinkToParentIfNeeded(buffer, field);

    buffer.append("<PRE>");
    generateFieldSignature(buffer, field, true);
    buffer.append("</PRE>");

    ColorUtil.appendColorPreview(field, buffer);

    PsiDocComment comment = getDocComment(field);
    if (comment != null) {
      generateCommonSection(buffer, comment);
    }

    if (generatePrologueAndEpilogue)
      generateEpilogue(buffer);
  }

  private static void generateFieldSignature(StringBuilder buffer, PsiField field, boolean generateLink) {
    generateAnnotations(buffer, field, generateLink, true, false);
    String modifiers = PsiFormatUtil.formatModifiers(field, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY);
    if (!modifiers.isEmpty()) {
      buffer.append(modifiers);
      buffer.append(" ");
    }
    generateType(buffer, field.getType(), field, generateLink);
    buffer.append(" ");
    buffer.append("<b>");
    buffer.append(field.getName());
    appendInitializer(buffer, field);
    enumConstantOrdinal(buffer, field, field.getContainingClass(), "\n");
    buffer.append("</b>");
  }

  public static void enumConstantOrdinal(StringBuilder buffer, PsiField field, PsiClass parentClass, final String newLine) {
    if (parentClass != null && field instanceof PsiEnumConstant) {
      final PsiField[] fields = parentClass.getFields();
      final int idx = ArrayUtilRt.find(fields, field);
      if (idx >= 0) {
        buffer.append(newLine);
        buffer.append("Enum constant ordinal: ").append(idx);
      }
    }
  }

  // not a javadoc in fact..
  private void generateVariableJavaDoc(@NonNls StringBuilder buffer, PsiVariable variable, boolean generatePrologueAndEpilogue) {
    if (generatePrologueAndEpilogue)
      generatePrologue(buffer);

    buffer.append("<PRE>");
    String modifiers = PsiFormatUtil.formatModifiers(variable, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY);
    if (!modifiers.isEmpty()) {
      buffer.append(modifiers);
      buffer.append(" ");
    }
    generateType(buffer, variable.getType(), variable);
    buffer.append(" ");
    buffer.append("<b>");
    buffer.append(variable.getName());
    appendInitializer(buffer, variable);
    buffer.append("</b>");
    buffer.append("</PRE>");

    ColorUtil.appendColorPreview(variable, buffer);

    if (generatePrologueAndEpilogue)
      generateEpilogue(buffer);
  }

  // not a javadoc in fact..
  private void generateFileJavaDoc(StringBuilder buffer, PsiFile file, boolean generatePrologueAndEpilogue) {
    if (generatePrologueAndEpilogue)
      generatePrologue(buffer);
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null) {
      buffer.append(virtualFile.getPresentableUrl());
    }
    if (generatePrologueAndEpilogue)
      generateEpilogue(buffer);
  }

  private void generatePackageJavaDoc(final StringBuilder buffer, final PsiPackage psiPackage, boolean generatePrologueAndEpilogue) {
    for (PsiDirectory directory : psiPackage.getDirectories(new EverythingGlobalScope(myProject))) {
      final PsiFile packageInfoFile = directory.findFile(PsiPackage.PACKAGE_INFO_FILE);
      if (packageInfoFile != null) {
        final ASTNode node = packageInfoFile.getNode();
        if (node != null) {
          final ASTNode docCommentNode = findRelevantCommentNode(node);
          if (docCommentNode != null) {
            final PsiDocComment docComment = (PsiDocComment)docCommentNode.getPsi();

            if (generatePrologueAndEpilogue)
              generatePrologue(buffer);

            generateCommonSection(buffer, docComment);

            if (generatePrologueAndEpilogue)
              generateEpilogue(buffer);
            break;
          }
        }
      }
      PsiFile packageHtmlFile = directory.findFile("package.html");
      if (packageHtmlFile != null) {
        generatePackageHtmlJavaDoc(buffer, packageHtmlFile, generatePrologueAndEpilogue);
        break;
      }
    }
  }

  /**
   * Finds doc comment immediately preceding package statement
   */
  @Nullable
  private static ASTNode findRelevantCommentNode(@NotNull ASTNode fileNode) {
    ASTNode node = fileNode.findChildByType(JavaElementType.PACKAGE_STATEMENT);
    if (node == null) node = fileNode.getLastChildNode();
    while (node != null && node.getElementType() != JavaDocElementType.DOC_COMMENT) {
      node = node.getTreePrev();
    }
    return node;
  }

  public void generateCommonSection(StringBuilder buffer, PsiDocComment docComment) {
    generateDescription(buffer, docComment);
    generateApiSection(buffer, docComment);
    generateDeprecatedSection(buffer, docComment);
    generateSinceSection(buffer, docComment);
    generateSeeAlsoSection(buffer, docComment);
  }

  private void generateApiSection(StringBuilder buffer, PsiDocComment comment) {
    final String[] tagNames = {"apiNote", "implSpec", "implNote"};
    for (String tagName : tagNames) {
      PsiDocTag tag = comment.findTagByName(tagName);
      if (tag != null) {
        buffer.append("<DD><DL>");
        buffer.append("<DT><b>").append(tagName).append("</b>");
        buffer.append("<DD>");
        generateValue(buffer, tag.getDataElements(), ourEmptyElementsProvider);
        buffer.append("</DD></DL></DD>");
      }
    }
  }

  private void generatePackageHtmlJavaDoc(final StringBuilder buffer, final PsiFile packageHtmlFile, boolean generatePrologueAndEpilogue) {
    String htmlText = packageHtmlFile.getText();

    try {
      final Document document = JDOMUtil.loadDocument(new ByteArrayInputStream(htmlText.getBytes(CharsetToolkit.UTF8_CHARSET)));
      final Element rootTag = document.getRootElement();
      final Element subTag = rootTag.getChild("body");
      if (subTag != null) {
        htmlText = subTag.getValue();
      }
    }
    catch (JDOMException ignore) {}
    catch (IOException ignore) {}

    htmlText = StringUtil.replace(htmlText, "*/", "&#42;&#47;");

    final String fileText = "/** " + htmlText + " */";
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(packageHtmlFile.getProject()).getElementFactory();
    final PsiDocComment docComment;
    try {
      docComment = elementFactory.createDocCommentFromText(fileText);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return;
    }

    if (generatePrologueAndEpilogue)
      generatePrologue(buffer);

    generateCommonSection(buffer, docComment);

    if (generatePrologueAndEpilogue)
      generateEpilogue(buffer);
  }

  public static @Nullable PsiExpression calcInitializerExpression(PsiVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    if (initializer != null) {
      PsiModifierList modifierList = variable.getModifierList();
      if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.FINAL) && !(initializer instanceof PsiLiteralExpression)) {
        JavaPsiFacade instance = JavaPsiFacade.getInstance(variable.getProject());
        Object o = instance.getConstantEvaluationHelper().computeConstantExpression(initializer);
        if (o != null) {
          String text = o.toString();
          PsiType type = variable.getType();
          if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            text = "\"" + StringUtil.escapeStringCharacters(StringUtil.shortenPathWithEllipsis(text, 120)) + "\"";
          }
          else if (type.equalsToText("char")) {
            text = "'" + text + "'";
          }
          try {
            return instance.getElementFactory().createExpressionFromText(text, variable);
          } catch (IncorrectOperationException ex) {
            LOG.info("type:" + type.getCanonicalText() + "; text: " + text, ex);
          }
        }
      }
    }
    return null;
  }

  public static boolean appendExpressionValue(StringBuilder buffer, PsiExpression initializer, String label) {
    String text = initializer.getText().trim();
    int index1 = text.indexOf('\n');
    if (index1 < 0) index1 = text.length();
    int index2 = text.indexOf('\r');
    if (index2 < 0) index2 = text.length();
    int index = Math.min(index1, index2);
    boolean trunc = index < text.length();
    text = text.substring(0, index);
    buffer.append(label);
    buffer.append(StringUtil.escapeXml(text));
    if (trunc) {
      buffer.append("...");
    }
    return trunc;
  }

  private static void appendInitializer(StringBuilder buffer, PsiVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    if (initializer != null) {
      buffer.append(" = ");

      String text = initializer.getText();
      text = text.trim();
      int index1 = text.indexOf('\n');
      if (index1 < 0) index1 = text.length();
      int index2 = text.indexOf('\r');
      if (index2 < 0) index2 = text.length();
      int index = Math.min(index1, index2);
      boolean trunc = index < text.length();
      if (trunc) {
        text = text.substring(0, index);
        buffer.append(StringUtil.escapeXml(text));
        buffer.append("...");
      }
      else {
        initializer.accept(new MyVisitor(buffer));
      }
      PsiExpression constantInitializer = calcInitializerExpression(variable);
      if (constantInitializer != null) {
        buffer.append("\n");
        appendExpressionValue(buffer, constantInitializer, CodeInsightBundle.message("javadoc.resolved.value"));
      }
    }
  }

  private static void generateAnnotations(@NonNls @NotNull StringBuilder buffer,
                                          @NotNull PsiModifierListOwner owner,
                                          boolean generateLink,
                                          boolean splitAnnotations,
                                          boolean useShortNames) {
    final PsiModifierList ownerModifierList = owner.getModifierList();
    if (ownerModifierList == null) return;
    generateAnnotations(buffer, owner, ownerModifierList.getAnnotations(), false, generateLink, splitAnnotations, useShortNames);
    PsiAnnotation[] externalAnnotations = ExternalAnnotationsManager.getInstance(owner.getProject()).findExternalAnnotations(owner);
    if (externalAnnotations == null) {
      externalAnnotations = PsiAnnotation.EMPTY_ARRAY;
    }
    PsiAnnotation[] inferredAnnotations = InferredAnnotationsManager.getInstance(owner.getProject()).findInferredAnnotations(owner);
    externalAnnotations = ArrayUtil.mergeArrays(externalAnnotations, inferredAnnotations, PsiAnnotation.ARRAY_FACTORY);
    generateAnnotations(buffer, owner, externalAnnotations, true, generateLink, splitAnnotations, useShortNames);
  }

  private static void generateAnnotations(StringBuilder buffer,
                                          PsiModifierListOwner owner,
                                          PsiAnnotation[] annotations,
                                          boolean external,
                                          boolean generateLink, boolean splitAnnotations, boolean useShortNames) {
    PsiManager manager = owner.getManager();

    Set<String> shownAnnotations = ContainerUtil.newHashSet();

    for (PsiAnnotation annotation : annotations) {
      final PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
      if (nameReferenceElement == null) continue;
      final PsiElement resolved = nameReferenceElement.resolve();
      boolean inferred = AnnotationUtil.isInferredAnnotation(annotation);
      String qualifiedName = annotation.getQualifiedName();
      if (!(shownAnnotations.add(qualifiedName) || isRepeatableAnnotationType(resolved))) {
        continue;
      }

      if (resolved instanceof PsiClass && 
          qualifiedName != null && JavaDocUtil.findReferenceTarget(owner.getManager(), qualifiedName, owner) != null) {
        final PsiClass annotationType = (PsiClass)resolved;
        if (isDocumentedAnnotationType(annotationType)) {
          if (inferred) buffer.append("<i>");
          final PsiClassType type = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createType(annotationType, PsiSubstitutor.EMPTY);
          buffer.append("@");
          if (inferred && !generateLink) {
            buffer.append(type.getPresentableText());
          } else {
            generateType(buffer, type, owner, generateLink, useShortNames && !external);
          }
          final PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
          if (attributes.length > 0) {
            buffer.append("(");
            boolean first = true;
            for (PsiNameValuePair pair : attributes) {
              if (!first) buffer.append(",&nbsp;");
              first = false;
              final String name = pair.getName();
              if (name != null) {
                buffer.append(name);
                buffer.append(" = ");
              }
              final PsiAnnotationMemberValue value = pair.getValue();
              if (value != null) {
                if (value instanceof PsiArrayInitializerMemberValue) {
                  buffer.append("{");
                  boolean firstMember = true;

                  for(PsiAnnotationMemberValue memberValue:((PsiArrayInitializerMemberValue)value).getInitializers()) {
                    if (!firstMember) buffer.append(",");
                    firstMember = false;

                    appendLinkOrText(buffer, memberValue, generateLink);
                  }
                  buffer.append("}");
                } else {
                  appendLinkOrText(buffer, value, generateLink);
                }
              }
            }
            buffer.append(")");
          }
          if (inferred) buffer.append("</i>");
          buffer.append("&nbsp;");
        }
      } else if (external) {
        if (inferred) buffer.append("<i>");
        String annoText = inferred ? "@" + annotation.getNameReferenceElement().getReferenceName() + annotation.getParameterList().getText()
                                   : annotation.getText();
        buffer.append(XmlStringUtil.escapeString(annoText));
        if (inferred) buffer.append("</i>");
        buffer.append("&nbsp;");
      }
      else {
        buffer.append("<font color=red>");
        buffer.append(XmlStringUtil.escapeString(annotation.getText()));
        buffer.append("</font>");
        buffer.append("&nbsp;");
      }
      if (splitAnnotations) buffer.append("\n");
    }
  }

  private static void appendLinkOrText(StringBuilder buffer,
                                       PsiAnnotationMemberValue memberValue,
                                       boolean generateLink) {
    if (generateLink && memberValue instanceof PsiQualifiedReferenceElement) {
      String text = ((PsiQualifiedReferenceElement)memberValue).getCanonicalText();
      PsiElement resolve = ((PsiQualifiedReferenceElement)memberValue).resolve();

      if (resolve instanceof PsiField) {
        PsiField field = (PsiField)resolve;
        PsiClass aClass = field.getContainingClass();
        int startOfPropertyNamePosition = text.lastIndexOf('.');

        if (startOfPropertyNamePosition != -1) {
          text = text.substring(0, startOfPropertyNamePosition) + '#' + text.substring(startOfPropertyNamePosition + 1);
        }
        else {
          if (aClass != null) text = aClass.getQualifiedName() + '#' + field.getName();
        }
        generateLink(buffer, text, aClass != null? aClass.getName() + '.' + field.getName():null, memberValue, false);
        return;
      }
    }

    buffer.append(XmlStringUtil.escapeString(memberValue.getText()));
  }

  public static boolean isDocumentedAnnotationType(@Nullable PsiElement annotationType) {
    return annotationType instanceof PsiClass && AnnotationUtil.isAnnotated((PsiClass)annotationType, "java.lang.annotation.Documented", false);
  }

  public static boolean isRepeatableAnnotationType(@Nullable PsiElement annotationType) {
    return annotationType instanceof PsiClass && AnnotationUtil.isAnnotated((PsiClass)annotationType, CommonClassNames.JAVA_LANG_ANNOTATION_REPEATABLE, false, true);
  }

  private void generateMethodParameterJavaDoc(@NonNls StringBuilder buffer, PsiParameter parameter, boolean generatePrologueAndEpilogue) {
    String parameterName = parameter.getName();
    
    if (generatePrologueAndEpilogue) {
      generatePrologue(buffer);
    }

    buffer.append("<PRE>");
    String modifiers = PsiFormatUtil.formatModifiers(parameter, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY);
    if (!modifiers.isEmpty()) {
      buffer.append(modifiers);
      buffer.append(" ");
    }
    generateAnnotations(buffer, parameter, true, true, false);
    generateType(buffer, parameter.getType(), parameter);
    buffer.append(" ");
    buffer.append("<b>");
    buffer.append(parameterName);
    appendInitializer(buffer, parameter);
    buffer.append("</b>");
    buffer.append("</PRE>");

    final PsiElement method = PsiTreeUtil.getParentOfType(parameter, PsiMethod.class, PsiLambdaExpression.class);

    if (method instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)method;
      PsiParameterList parameterList = psiMethod.getParameterList();
      if (parameter.getParent() == parameterList) { // this can also be a parameter in foreach statement or in catch clause
        final PsiDocComment docComment = getDocComment(psiMethod);
        final PsiDocTag[] localTags = docComment != null ? docComment.getTags() : PsiDocTag.EMPTY_ARRAY;
        int parameterIndex = parameterList.getParameterIndex(parameter);
        final ParamInfo tagInfoProvider = findDocTag(localTags, parameterName, psiMethod, parameterLocator(parameterIndex));

        if (tagInfoProvider != null) {
          generateOneParameter(buffer, tagInfoProvider);
        }
      }
    }

    if (generatePrologueAndEpilogue)
      generateEpilogue(buffer);
  }

  private void generateMethodJavaDoc(@NonNls StringBuilder buffer, PsiMethod method, boolean generatePrologueAndEpilogue) {
    if (generatePrologueAndEpilogue)
      generatePrologue(buffer);

    generateLinkToParentIfNeeded(buffer, method);

    buffer.append("<PRE>");
    generateMethodSignature(buffer, method, true, false);
    buffer.append("</PRE>");

    PsiDocComment comment = getMethodDocComment(method);

    generateMethodDescription(buffer, method, comment);

    generateSuperMethodsSection(buffer, method, false);
    generateSuperMethodsSection(buffer, method, true);

    if (comment != null) {
      generateDeprecatedSection(buffer, comment);
    }

    generateParametersSection(buffer, method, comment);
    generateTypeParametersSection(buffer, method, comment);
    generateReturnsSection(buffer, method, comment);
    generateThrowsSection(buffer, method, comment);

    if (comment != null) {
      generateApiSection(buffer, comment);
      generateSinceSection(buffer, comment);
      generateSeeAlsoSection(buffer, comment);
    }

    if (generatePrologueAndEpilogue)
      generateEpilogue(buffer);
  }

  private static void generateLinkToParentIfNeeded(StringBuilder buffer, PsiMember member) {
    PsiClass parentClass = member.getContainingClass();
    if (parentClass != null) {
      String qName = parentClass.getQualifiedName();
      if (qName != null) {
        buffer.append("<small><b>");
        generateLink(buffer, qName, qName, member, false);
        buffer.append("</b></small>");
      }
    }
  }

  private static void generateMethodSignature(StringBuilder buffer, PsiMethod method, boolean generateLink, boolean useShortNames) {
    generateAnnotations(buffer, method, generateLink, true, useShortNames);
    String modifiers = PsiFormatUtil.formatModifiers(method, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY);
    int indent = 0;
    if (!modifiers.isEmpty()) {
      buffer.append(modifiers);
      buffer.append("&nbsp;");
      indent += modifiers.length() + 1;
    }

    final String typeParamsString = generateTypeParameters(method, useShortNames);
    indent += StringUtil.unescapeXml(StringUtil.stripHtml(typeParamsString, true)).length();
    if (!typeParamsString.isEmpty()) {
      buffer.append(typeParamsString);
      buffer.append("&nbsp;");
      indent++;
    }

    if (method.getReturnType() != null) {
      indent += generateType(buffer, method.getReturnType(), method, generateLink, useShortNames);
      buffer.append("&nbsp;");
      indent++;
    }
    buffer.append("<b>");
    String name = method.getName();
    buffer.append(name);
    buffer.append("</b>");
    indent += name.length();

    buffer.append("(");

    PsiParameter[] parms = method.getParameterList().getParameters();
    for (int i = 0; i < parms.length; i++) {
      PsiParameter parm = parms[i];
      generateAnnotations(buffer, parm, generateLink, false, useShortNames);
      generateType(buffer, parm.getType(), method, generateLink, useShortNames);
      buffer.append("&nbsp;");
      if (parm.getName() != null) {
        buffer.append(parm.getName());
      }
      if (i < parms.length - 1) {
        buffer.append(",\n ");
        for (int j = 0; j < indent; j++) {
          buffer.append(" ");
        }
      }
    }
    buffer.append(")");

    PsiClassType[] refs = method.getThrowsList().getReferencedTypes();
    if (refs.length > 0) {
      buffer.append("\n");
      indent -= THROWS_KEYWORD.length() + 1;
      for (int i = 0; i < indent; i++) {
        buffer.append(" ");
      }
      indent += THROWS_KEYWORD.length() + 1;
      buffer.append(THROWS_KEYWORD);
      buffer.append("&nbsp;");
      for (int i = 0; i < refs.length; i++) {
        generateLink(buffer, useShortNames ? refs[i].getPresentableText() : refs[i].getCanonicalText(), null, method, false);
        if (i < refs.length - 1) {
          buffer.append(",\n");
          for (int j = 0; j < indent; j++) {
            buffer.append(" ");
          }
        }
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private PsiDocComment getMethodDocComment(final PsiMethod method) {
    final PsiClass parentClass = method.getContainingClass();
    if (parentClass != null && parentClass.isEnum()) {
      final PsiParameterList parameterList = method.getParameterList();
      if (method.getName().equals("values") && parameterList.getParametersCount() == 0) {
        return loadSyntheticDocComment(method, "/javadoc/EnumValues.java.template");
      }
      if (method.getName().equals("valueOf") && parameterList.getParametersCount() == 1) {
        final PsiType psiType = parameterList.getParameters()[0].getType();
        if (psiType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          return loadSyntheticDocComment(method, "/javadoc/EnumValueOf.java.template");
        }
      }
    }
    return getDocComment(method);
  }

  private PsiDocComment loadSyntheticDocComment(final PsiMethod method, final String resourceName) {
    //noinspection IOResourceOpenedButNotSafelyClosed
    final InputStream commentStream = JavaDocInfoGenerator.class.getResourceAsStream(resourceName);
    if (commentStream == null) {
      return null;
    }

    final StringBuilder buffer;
    try {
      buffer = new StringBuilder();
      try {
        for (int ch; (ch = commentStream.read()) != -1;) {
          buffer.append((char)ch);
        }
      }
      catch (IOException e) {
        return null;
      }
    }
    finally {
      try {
        commentStream.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    String s = buffer.toString();
    PsiClass containingClass = method.getContainingClass();
    assert containingClass != null;
    String containingClassName = containingClass.getName();
    assert containingClassName != null;
    s = StringUtil.replace(s, "<ClassName>", containingClassName);
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    try {
      return elementFactory.createDocCommentFromText(s);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  protected void generatePrologue(StringBuilder buffer) {
    URL baseUrl = getBaseUrl();
    buffer.append("<html><head>");
    if (baseUrl != null) {
      buffer.append("<base href=\"").append(baseUrl).append("\">");
    }
    buffer.append("    <style type=\"text/css\">" +
                  "        #error {" +
                  "            background-color: #eeeeee;" +
                  "            margin-bottom: 10px;" +
                  "        }" +
                  "        p {" +
                  "            margin: 5px 0;" +
                  "        }" +
                  "    </style>" +
                  "</head><body>");
  }
  
  private URL getBaseUrl() {
    if (myElement == null) return null;
    PsiElement element = myElement.getNavigationElement();
    if (element == null) return null;
    PsiFile file = element.getContainingFile();
    if (file == null) return null;
    VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) return null;
    return VfsUtilCore.convertToURL(vFile.getUrl());
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  protected void generateEpilogue(StringBuilder buffer) {
    while (true) {
      if (buffer.length() < BR_TAG.length()) break;
      char c = buffer.charAt(buffer.length() - 1);
      if (c == '\n' || c == '\r' || c == ' ' || c == '\t') {
        buffer.setLength(buffer.length() - 1);
        continue;
      }
      String tail = buffer.substring(buffer.length() - BR_TAG.length());
      if (tail.equalsIgnoreCase(BR_TAG)) {
        buffer.setLength(buffer.length() - BR_TAG.length());
        continue;
      }
      break;
    }
    buffer.append("</body></html>");
  }

  private void generateDescription(StringBuilder buffer, PsiDocComment comment) {
    PsiElement[] elements = comment.getDescriptionElements();
    generateValue(buffer, elements, ourEmptyElementsProvider);
  }

  private static boolean isEmptyDescription(PsiDocComment comment) {
    if (comment == null) {
      return true;
    }
    PsiElement[] descriptionElements = comment.getDescriptionElements();

    for (PsiElement description : descriptionElements) {
      String text = description.getText();

      if (text != null) {
        if (!ourWhitespaces.matcher(text).replaceAll("").isEmpty()) {
          return false;
        }
      }
    }

    return true;
  }

  private void generateMethodDescription(@NonNls StringBuilder buffer, final PsiMethod method, final PsiDocComment comment) {
    final DocTagLocator<PsiElement[]> descriptionLocator = new DocTagLocator<PsiElement[]>() {
      @Override
      public PsiElement[] find(PsiDocCommentOwner owner, PsiDocComment comment) {
        if (comment == null) {
          return null;
        }

        if (isEmptyDescription(comment)) {
          return null;
        }

        return comment.getDescriptionElements();
      }
    };

    if (comment != null) {
      if (!isEmptyDescription(comment)) {
        generateValue
          (buffer, comment.getDescriptionElements(),
           new InheritDocProvider<PsiElement[]>() {
             @Override
             public Pair<PsiElement[], InheritDocProvider<PsiElement[]>> getInheritDoc() {
               return findInheritDocTag(method, descriptionLocator);
             }

             @Override
             public PsiClass getElement() {
               return method.getContainingClass();
             }
           });
        return;
      }
    }

    Pair<PsiElement[], InheritDocProvider<PsiElement[]>> pair = findInheritDocTag(method, descriptionLocator);

    if (pair != null) {
      PsiElement[] elements = pair.first;
      PsiClass extendee = pair.second.getElement();

      if (elements != null) {
        buffer.append("<DD><DL>");
        buffer.append("<DT><b>");
        buffer.append(CodeInsightBundle.message( extendee.isInterface() ? "javadoc.description.copied.from.interface"
                                                                        : "javadoc.description.copied.from.class"));
        buffer.append("</b>&nbsp;");
        generateLink(buffer, extendee, JavaDocUtil.getShortestClassName(extendee, method), false);
        buffer.append(BR_TAG);
        generateValue(buffer, elements, pair.second);
        buffer.append("</DD></DL></DD>");
      }
    }
  }

  private void generateValue(StringBuilder buffer, PsiElement[] elements, InheritDocProvider<PsiElement[]> provider) {
    generateValue(buffer, elements, 0, provider);
  }

  private String getDocRoot() {
    PsiClass aClass;

    if (myElement instanceof PsiClass) {
      aClass = (PsiClass)myElement;
    }
    else if (myElement instanceof PsiMember) {
      aClass = ((PsiMember)myElement).getContainingClass();
    }
    else {
      aClass = PsiTreeUtil.getParentOfType(myElement, PsiClass.class);
    }

    if (aClass == null) {
      return "";
    }

    String qName = aClass.getQualifiedName();
    if (qName == null) {
      return "";
    }

    return StringUtil.repeat("../", StringUtil.countChars(qName, '.') + 1);
  }

  private void generateValue(StringBuilder buffer,
                             PsiElement[] elements,
                             int startIndex,
                             InheritDocProvider<PsiElement[]> provider) {
    int predictOffset =
      startIndex < elements.length ?
      elements[startIndex].getTextOffset() + elements[startIndex].getText().length() :
      0;

    for (int i = startIndex; i < elements.length; i++) {
      if (elements[i].getTextOffset() > predictOffset) buffer.append(" ");
      predictOffset = elements[i].getTextOffset() + elements[i].getText().length();
      PsiElement element = elements[i];
      if (element instanceof PsiInlineDocTag) {
        PsiInlineDocTag tag = (PsiInlineDocTag)element;
        final String tagName = tag.getName();
        if (tagName.equals(LINK_TAG)) {
          generateLinkValue(tag, buffer, false);
        }
        else if (tagName.equals(LITERAL_TAG)) {
          generateLiteralValue(buffer, tag);
        }
        else if (tagName.equals(CODE_TAG)) {
          generateCodeValue(tag, buffer);
        }
        else if (tagName.equals(LINKPLAIN_TAG)) {
          generateLinkValue(tag, buffer, true);
        }
        else if (tagName.equals(INHERITDOC_TAG)) {
          Pair<PsiElement[], InheritDocProvider<PsiElement[]>> inheritInfo = provider.getInheritDoc();

          if (inheritInfo != null) {
            generateValue(buffer, inheritInfo.first, inheritInfo.second);
          }
        }
        else if (tagName.equals(DOCROOT_TAG)) {
          buffer.append(getDocRoot());
        }
        else if (tagName.equals(VALUE_TAG)) {
          generateValueValue(tag, buffer, element);
        }
      }
      else {
        buffer.append(StringUtil.replaceUnicodeEscapeSequences(element.getText()));
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void generateCodeValue(PsiInlineDocTag tag, StringBuilder buffer) {
    buffer.append("<code>");
    generateLiteralValue(buffer, tag);
    buffer.append("</code>");
  }

  private void generateLiteralValue(StringBuilder buffer, PsiDocTag tag) {
    StringBuilder tmpBuffer = new StringBuilder();
    for (PsiElement element : tag.getDataElements()) {
      appendPlainText(StringUtil.escapeXml(element.getText()), tmpBuffer);
    }
    if ((mySdkVersion == null || mySdkVersion.isAtLeast(JavaSdkVersion.JDK_1_8)) && isInPre(tag)) {
      buffer.append(tmpBuffer);
    }
    else {
      buffer.append(StringUtil.trimLeading(tmpBuffer));
    }
  }
  
  private static boolean isInPre(PsiDocTag tag) {
    PsiElement sibling = tag.getPrevSibling();
    while (sibling != null) {
      if (sibling instanceof PsiDocToken) {
        String text = sibling.getText().toLowerCase();
        int pos = text.lastIndexOf("pre>");
        if (pos > 0) {
          switch (text.charAt(pos - 1)) {
            case '<' : return true;
            case '/' : return false;
          }
        }
      }
      sibling = sibling.getPrevSibling();
    }
    return false;
  }

  private static void appendPlainText(@NonNls String text, final StringBuilder buffer) {
    buffer.append(StringUtil.replaceUnicodeEscapeSequences(text));
  }

  private static void generateLinkValue(PsiInlineDocTag tag, StringBuilder buffer, boolean plainLink) {
    PsiElement[] tagElements = tag.getDataElements();
    String text = createLinkText(tagElements);
    if (!text.isEmpty()) {
      int index = JavaDocUtil.extractReference(text);
      String refText = text.substring(0, index).trim();
      String label = text.substring(index).trim();
      if (label.isEmpty()) {
        label = null;
      }
      generateLink(buffer, refText, label, tagElements[0], plainLink);
    }
  }

  private void generateValueValue(final PsiInlineDocTag tag, final StringBuilder buffer, final PsiElement element) {
    String text = createLinkText(tag.getDataElements());
    PsiField valueField = null;
    if (text.isEmpty()) {
      if (myElement instanceof PsiField) valueField = (PsiField) myElement;
    }
    else {
      if (text.indexOf('#') == -1) {
        text = "#" + text;
      }
      PsiElement target = JavaDocUtil.findReferenceTarget(PsiManager.getInstance(myProject), text, myElement);
      if (target instanceof PsiField) {
        valueField = (PsiField) target;
      }
    }

    Object value = null;
    if (valueField != null) {
      PsiExpression initializer = valueField.getInitializer();
      value = JavaConstantExpressionEvaluator.computeConstantExpression(initializer, false);
    }

    if (value != null) {
      buffer.append(value);
    }
    else {
      buffer.append(element.getText());
    }
  }

  private static String createLinkText(final PsiElement[] tagElements) {
    int predictOffset = tagElements.length > 0 ? tagElements[0].getTextOffset() + tagElements[0].getText().length() : 0;
    StringBuilder buffer = new StringBuilder();
    for (int j = 0; j < tagElements.length; j++) {
      PsiElement tagElement = tagElements[j];

      if (tagElement.getTextOffset() > predictOffset) buffer.append(" ");
      predictOffset = tagElement.getTextOffset() + tagElement.getText().length();

      collectElementText(buffer, tagElement);

      if (j < tagElements.length - 1) {
        buffer.append(" ");
      }
    }
    return buffer.toString().trim();
  }

  private static void collectElementText(final StringBuilder buffer, PsiElement element) {
    element.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);
        if (element instanceof PsiWhiteSpace ||
            element instanceof PsiJavaToken ||
            element instanceof PsiDocToken && ((PsiDocToken)element).getTokenType() != JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
          buffer.append(element.getText());
        }
      }
    });
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void generateDeprecatedSection(StringBuilder buffer, PsiDocComment comment) {
    PsiDocTag tag = comment.findTagByName("deprecated");
    if (tag != null) {
      buffer.append("<DD><DL>");
      buffer.append("<B>").append(CodeInsightBundle.message("javadoc.deprecated")).append("</B>&nbsp;");
      buffer.append("<I>");
      generateValue(buffer, tag.getDataElements(), ourEmptyElementsProvider);
      buffer.append("</I>");
      buffer.append("</DL></DD>");
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void generateSinceSection(StringBuilder buffer, PsiDocComment comment) {
    PsiDocTag tag = comment.findTagByName("since");
    if (tag != null) {
      buffer.append("<DD><DL>");
      buffer.append("<DT><b>").append(CodeInsightBundle.message("javadoc.since")).append("</b>");
      buffer.append("<DD>");
      generateValue(buffer, tag.getDataElements(), ourEmptyElementsProvider);
      buffer.append("</DD></DL></DD>");
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void generateSeeAlsoSection(StringBuilder buffer, PsiDocComment comment) {
    PsiDocTag[] tags = comment.findTagsByName("see");
    if (tags.length > 0) {
      buffer.append("<DD><DL>");
      buffer.append("<DT><b>").append(CodeInsightBundle.message("javadoc.see.also")).append("</b>");
      buffer.append("<DD>");
      for (int i = 0; i < tags.length; i++) {
        PsiDocTag tag = tags[i];
        PsiElement[] elements = tag.getDataElements();
        if (elements.length > 0) {
          String text = createLinkText(elements);

          if (text.startsWith("<")) {
            buffer.append(text);
          }
          else if (text.startsWith("\"")) {
            appendPlainText(text, buffer);
          }
          else {
            int index = JavaDocUtil.extractReference(text);
            String refText = text.substring(0, index).trim();
            String label = text.substring(index).trim();
            if (label.isEmpty()) {
              label = null;
            }
            generateLink(buffer, refText, label, comment, false);
          }
        }
        if (i < tags.length - 1) {
          buffer.append(",\n");
        }
      }
      buffer.append("</DD></DL></DD>");
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void generateParametersSection(StringBuilder buffer, final PsiMethod method, final PsiDocComment comment) {
    PsiParameter[] params = method.getParameterList().getParameters();
    PsiDocTag[] localTags = comment != null ? comment.findTagsByName("param") : PsiDocTag.EMPTY_ARRAY;

    LinkedList<ParamInfo> collectedTags = new LinkedList<>();

    for (int i = 0; i < params.length; i++) {
      PsiParameter param = params[i];
      final String paramName = param.getName();
      DocTagLocator<PsiDocTag> tagLocator = parameterLocator(i);
      ParamInfo parmTag = findDocTag(localTags, paramName, method, tagLocator);

      if (parmTag != null) {
        collectedTags.addLast(parmTag);
      }
    }

    generateParametersSection(buffer, CodeInsightBundle.message("javadoc.parameters"), collectedTags);
  }

  private void generateTypeParametersSection(final StringBuilder buffer, final PsiMethod method, PsiDocComment comment) {
    final PsiDocTag[] localTags = comment == null ? PsiDocTag.EMPTY_ARRAY : comment.findTagsByName("param");
    final PsiTypeParameter[] typeParameters = method.getTypeParameters();
    final LinkedList<ParamInfo> collectedTags = new LinkedList<>();
    for (int i = 0; i < typeParameters.length; i++) {
      PsiTypeParameter typeParameter = typeParameters[i];
      final String paramName = "<" + typeParameter.getName() + ">";
      DocTagLocator<PsiDocTag> tagLocator = typeParameterLocator(i);
      ParamInfo parmTag = findDocTag(localTags, paramName, method, tagLocator);

      if (parmTag != null) {
        collectedTags.addLast(parmTag);
      }
    }
    generateParametersSection(buffer, CodeInsightBundle.message("javadoc.type.parameters"), collectedTags);
  }

  private void generateParametersSection(StringBuilder buffer, String titleMessage, LinkedList<ParamInfo> collectedTags) {
    if (!collectedTags.isEmpty()) {
      buffer.append("<DD><DL>");
      buffer.append("<DT><b>").append(titleMessage).append("</b>");
      for (ParamInfo tag : collectedTags) {
        generateOneParameter(buffer, tag);
      }
      buffer.append("</DD></DL></DD>");
    }
  }

  @Nullable private ParamInfo findDocTag(final PsiDocTag[] localTags, 
                                         final String paramName, final PsiMethod method, final DocTagLocator<PsiDocTag> tagLocator) {
    PsiDocTag localTag = getTagByName(localTags, paramName);
    if (localTag != null) {
      return new ParamInfo(paramName, localTag, new InheritDocProvider<PsiDocTag>() {
        @Override
        public Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> getInheritDoc() {
          return findInheritDocTag(method, tagLocator);
        }

        @Override
        public PsiClass getElement() {
          return method.getContainingClass();
        }
      });
    }
    Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> tag = findInheritDocTag(method, tagLocator);
    return tag == null ? null : new ParamInfo(paramName, tag);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void generateOneParameter(StringBuilder buffer, ParamInfo tag) {
    PsiElement[] elements = tag.docTag.getDataElements();
    if (elements.length == 0) return;
    String text = elements[0].getText();
    buffer.append("<DD>");
    int spaceIndex = text.indexOf(' ');
    if (spaceIndex < 0) {
      spaceIndex = text.length();
    }
    buffer.append("<code>");
    buffer.append(StringUtil.escapeXml(tag.name));
    buffer.append("</code>");
    buffer.append(" - ");
    buffer.append(text.substring(spaceIndex));
    generateValue(buffer, elements, 1, mapProvider(tag.inheritDocTagProvider, true));
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void generateReturnsSection(StringBuilder buffer, final PsiMethod method, final PsiDocComment comment) {
    PsiDocTag tag = comment == null ? null : comment.findTagByName("return");
    Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> pair =
      tag == null ? null : new Pair<>(tag,
                                      new InheritDocProvider<PsiDocTag>() {
                                        @Override
                                        public Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> getInheritDoc() {
                                          return findInheritDocTag(method, new ReturnTagLocator());
                                        }

                                        @Override
                                        public PsiClass getElement() {
                                          return method.getContainingClass();
                                        }
                                      });

    if (pair == null && myElement instanceof PsiMethod) {
      pair = findInheritDocTag((PsiMethod)myElement, new ReturnTagLocator());
    }

    if (pair != null) {
      buffer.append("<DD><DL>");
      buffer.append("<DT><b>").append(CodeInsightBundle.message("javadoc.returns")).append("</b>");
      buffer.append("<DD>");
      generateValue(buffer, pair.first.getDataElements(), mapProvider(pair.second, false));
      buffer.append("</DD></DL></DD>");
    }
  }

  private static PsiDocTag[] getThrowsTags(PsiDocComment comment) {
    if (comment == null) {
      return PsiDocTag.EMPTY_ARRAY;
    }

    PsiDocTag[] tags1 = comment.findTagsByName(THROWS_KEYWORD);
    PsiDocTag[] tags2 = comment.findTagsByName("exception");

    return ArrayUtil.mergeArrays(tags1, tags2);
  }

  private static boolean areWeakEqual(String one, String two) {
    return one.equals(two) || one.endsWith("." + two) || two.endsWith("." + one);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void generateThrowsSection(StringBuilder buffer, final PsiMethod method, final PsiDocComment comment) {
    final PsiDocTag[] localTags = getThrowsTags(comment);

    LinkedList<Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>> collectedTags =
      new LinkedList<>();

    final List<PsiClassType> declaredThrows = new ArrayList<>(Arrays.asList(method.getThrowsList().getReferencedTypes()));

    for (int i = localTags.length - 1; i > -1; i--) {
      PsiDocTagValue valueElement = localTags[i].getValueElement();

      if (valueElement != null) {
        for (Iterator<PsiClassType> iterator = declaredThrows.iterator(); iterator.hasNext();) {
          PsiClassType classType = iterator.next();
          if (Comparing.strEqual(valueElement.getText(), classType.getClassName()) || Comparing.strEqual(valueElement.getText(), classType.getCanonicalText())) {
            iterator.remove();
            break;
          }
        }

        final Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> tag = findInheritDocTag(method, exceptionLocator(valueElement.getText()));
        collectedTags.addFirst(new Pair<>(localTags[i], new InheritDocProvider<PsiDocTag>() {
          @Override
          public Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> getInheritDoc() {
            return tag;
          }

          @Override
          public PsiClass getElement() {
            return method.getContainingClass();
          }
        }));
      }
    }


    PsiClassType[] trousers = declaredThrows.toArray(new PsiClassType[declaredThrows.size()]);

    for (PsiClassType trouser : trousers) {
      if (trouser != null) {
        String paramName = trouser.getCanonicalText();
        Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> parmTag = null;

        for (PsiDocTag localTag : localTags) {
          PsiDocTagValue value = localTag.getValueElement();

          if (value != null) {
            String tagName = value.getText();

            if (tagName != null && areWeakEqual(tagName, paramName)) {
              parmTag = Pair.create(localTag, ourEmptyProvider);
              break;
            }
          }
        }

        if (parmTag == null) {
          parmTag = findInheritDocTag(method, exceptionLocator(paramName));
        }

        if (parmTag != null) {
          collectedTags.addLast(parmTag);
        }
        else {
          try {
            final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
            final PsiDocTag tag = elementFactory.createDocTagFromText("@exception " + paramName);
            collectedTags.addLast(Pair.create(tag, ourEmptyProvider));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    }

    if (!collectedTags.isEmpty()) {
      buffer.append("<DD><DL>");
      buffer.append("<DT><b>").append(CodeInsightBundle.message("javadoc.throws")).append("</b>");
      for (Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> tag : collectedTags) {
        PsiElement[] elements = tag.first.getDataElements();
        if (elements.length == 0) continue;
        buffer.append("<DD>");
        String text = elements[0].getText();
        int index = JavaDocUtil.extractReference(text);
        String refText = text.substring(0, index).trim();
        generateLink(buffer, refText, null, method, false);
        String rest = text.substring(index);
        if (!rest.isEmpty() || elements.length > 1) buffer.append(" - ");
        buffer.append(rest);
        generateValue(buffer, elements, 1, mapProvider(tag.second, true));
      }
      buffer.append("</DD></DL></DD>");
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void generateSuperMethodsSection(StringBuilder buffer, PsiMethod method, boolean overrides) {
    PsiClass parentClass = method.getContainingClass();
    if (parentClass == null) return;
    if (parentClass.isInterface() && !overrides) return;
    PsiMethod[] supers = method.findSuperMethods();
    if (supers.length == 0) return;
    boolean headerGenerated = false;
    for (PsiMethod superMethod : supers) {
      boolean isAbstract = superMethod.hasModifierProperty(PsiModifier.ABSTRACT);
      if (overrides) {
        if (parentClass.isInterface() != isAbstract) continue;
      }
      else {
        if (!isAbstract) continue;
      }
      PsiClass superClass = superMethod.getContainingClass();
      if (superClass == null) continue;
      if (!headerGenerated) {
        buffer.append("<DD><DL>");
        buffer.append("<DT><b>");
        buffer.append(CodeInsightBundle.message(overrides ? "javadoc.method.overrides" : "javadoc.method.specified.by"));
        buffer.append("</b>");
        headerGenerated = true;
      }
      buffer.append("<DD>");

      StringBuilder methodBuffer = new StringBuilder();
      generateLink(methodBuffer, superMethod, superMethod.getName(), false);
      StringBuilder classBuffer = new StringBuilder();
      generateLink(classBuffer, superClass, superClass.getName(), false);
      if (superClass.isInterface()) {
        buffer.append(CodeInsightBundle.message("javadoc.method.in.interface", methodBuffer.toString(), classBuffer.toString()));
      }
      else {
        buffer.append(CodeInsightBundle.message("javadoc.method.in.class", methodBuffer.toString(), classBuffer.toString()));
      }
    }
    if (headerGenerated) {
      buffer.append("</DD></DL></DD>");
    }
  }

  private static void generateLink(StringBuilder buffer, PsiElement element, String label, boolean plainLink) {
    String refText = JavaDocUtil.getReferenceText(element.getProject(), element);
    if (refText != null) {
      DocumentationManagerUtil.createHyperlink(buffer, element, refText, label, plainLink);
      //return generateLink(buffer, refText, label, context, false);
    }
  }

  /**
   * @return Length of the generated label.
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  private static int generateLink(StringBuilder buffer, String refText, String label, @NotNull PsiElement context, boolean plainLink) {
    if (label == null) {
      final PsiManager manager = context.getManager();
      label = JavaDocUtil.getLabelText(manager.getProject(), manager, refText, context);
    }

    LOG.assertTrue(refText != null, "refText appears to be null.");

    final PsiElement target = JavaDocUtil.findReferenceTarget(context.getManager(), refText, context);
    boolean isBrokenLink = target == null;
    if (isBrokenLink) {
      buffer.append("<font color=red>");
      buffer.append(label);
      buffer.append("</font>");
      return StringUtil.stripHtml(label, true).length();
    }


    generateLink(buffer, target, label, plainLink);
    return StringUtil.stripHtml(label, true).length();
  }

  /**
   * @return Length of the generated label.
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static int generateType(StringBuilder buffer, PsiType type, PsiElement context) {
    return generateType(buffer, type, context, true);
  }

  /**
   * @return Length of the generated label.
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static int generateType(StringBuilder buffer, PsiType type, PsiElement context, boolean generateLink) {
    return generateType(buffer, type, context, generateLink, false);
  }

  /**
   * @return Length of the generated label.
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static int generateType(StringBuilder buffer, PsiType type, PsiElement context, boolean generateLink, boolean useShortNames) {
    if (type instanceof PsiPrimitiveType) {
      String text = StringUtil.escapeXml(type.getCanonicalText());
      buffer.append(text);
      return text.length();
    }

    if (type instanceof PsiArrayType) {
      int rest = generateType(buffer, ((PsiArrayType)type).getComponentType(), context, generateLink, useShortNames);
      if (type instanceof PsiEllipsisType) {
        buffer.append("...");
        return rest + 3;
      }
      else {
        buffer.append("[]");
        return rest + 2;
      }
    }

    if (type instanceof PsiCapturedWildcardType) {
      type = ((PsiCapturedWildcardType)type).getWildcard();
    }

    if (type instanceof PsiWildcardType){
      PsiWildcardType wt = (PsiWildcardType)type;

      buffer.append("?");

      PsiType bound = wt.getBound();

      if (bound != null){
        final String keyword = wt.isExtends() ? " extends " : " super ";
        buffer.append(keyword);
        return generateType(buffer, bound, context, generateLink, useShortNames) + 1 + keyword.length();
      }

      return 1;
    }

    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = ((PsiClassType)type).resolveGenerics();
      PsiClass psiClass = result.getElement();
      PsiSubstitutor psiSubst = result.getSubstitutor();

      if (psiClass == null) {
        String canonicalText = type.getCanonicalText();
        String text = "<font color=red>" + StringUtil.escapeXml(canonicalText) + "</font>";
        buffer.append(text);
        return canonicalText.length();
      }

      String qName = psiClass.getQualifiedName();

      if (qName == null || psiClass instanceof PsiTypeParameter) {
        String text = StringUtil.escapeXml(useShortNames ? type.getPresentableText() : type.getCanonicalText());
        buffer.append(text);
        return text.length();
      }

      String name = useShortNames ? ((PsiClassType)type).rawType().getPresentableText() : qName;

      int length;
      if (generateLink) {
        length = generateLink(buffer, name, null, context, false);
      }
      else {
        buffer.append(name);
        length = buffer.length();
      }

      if (psiClass.hasTypeParameters()) {
        StringBuilder subst = new StringBuilder();

        PsiTypeParameter[] params = psiClass.getTypeParameters();

        subst.append(LT);
        length += 1;
        boolean goodSubst = true;
        for (int i = 0; i < params.length; i++) {
          PsiType t = psiSubst.substitute(params[i]);

          if (t == null) {
            goodSubst = false;
            break;
          }

          length += generateType(subst, t, context, generateLink, useShortNames);

          if (i < params.length - 1) {
            subst.append(", ");
          }
        }

        subst.append(GT);
        length += 1;
        if (goodSubst) {
          String text = subst.toString();

          buffer.append(text);
        }
      }

      return length;
    }

    if (type instanceof PsiDisjunctionType || type instanceof PsiIntersectionType) {
      if (!generateLink) {
        String canonicalText = useShortNames ? type.getPresentableText() : type.getCanonicalText();
        final String text = StringUtil.escapeXml(canonicalText);
        buffer.append(text);
        return canonicalText.length();
      }
      else {
        final String separator = type instanceof PsiDisjunctionType ? " | " : " & ";
        final List<PsiType> componentTypes;
        if (type instanceof PsiIntersectionType) {
          componentTypes = Arrays.asList(((PsiIntersectionType)type).getConjuncts());
        }
        else {
          componentTypes = ((PsiDisjunctionType)type).getDisjunctions();
        }
        int length = 0;
        for (PsiType psiType : componentTypes) {
          if (length > 0) {
            buffer.append(separator);
            length += 3;
          }
          length += generateType(buffer, psiType, context, true, useShortNames);
        }
        return length;
      }
    }

    return 0;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String generateTypeParameters(PsiTypeParameterListOwner owner, boolean useShortNames) {
    if (owner.hasTypeParameters()) {
      PsiTypeParameter[] parms = owner.getTypeParameters();

      StringBuilder buffer = new StringBuilder();

      buffer.append(LT);

      for (int i = 0; i < parms.length; i++) {
        PsiTypeParameter p = parms[i];

        buffer.append(p.getName());

        PsiClassType[] refs = JavaDocUtil.getExtendsList(p);

        if (refs.length > 0) {
          buffer.append(" extends ");

          for (int j = 0; j < refs.length; j++) {
            generateType(buffer, refs[j], owner, true, useShortNames);

            if (j < refs.length - 1) {
              buffer.append(" & ");
            }
          }
        }

        if (i < parms.length - 1) {
          buffer.append(", ");
        }
      }

      buffer.append(GT);

      return buffer.toString();
    }

    return "";
  }

  private <T> Pair<T, InheritDocProvider<T>> searchDocTagInOverridenMethod(PsiMethod method,
                                                                           final PsiClass aSuper,
                                                                           final DocTagLocator<T> loc) {
    if (aSuper != null) {

      final PsiMethod overriden =  findMethodInSuperClass(method, aSuper);

      if (overriden != null) {
        T tag = loc.find(overriden, getDocComment(overriden));

        if (tag != null) {
          return new Pair<>
            (tag,
             new InheritDocProvider<T>() {
               @Override
               public Pair<T, InheritDocProvider<T>> getInheritDoc() {
                 return findInheritDocTag(overriden, loc);
               }

               @Override
               public PsiClass getElement() {
                 return aSuper;
               }
             });
        }
      }
    }

    return null;
  }

  @Nullable
  private static PsiMethod findMethodInSuperClass(PsiMethod method, PsiClass aSuper) {
    for (PsiMethod superMethod : method.findDeepestSuperMethods()) {
      PsiMethod overriden = aSuper.findMethodBySignature(superMethod, false);
      if (overriden != null) {
        return overriden;
      }
    }
    return null;
  }

  @Nullable
  private <T> Pair<T, InheritDocProvider<T>> searchDocTagInSupers(PsiClassType[] supers,
                                                                  PsiMethod method,
                                                                  DocTagLocator<T> loc,
                                                                  Set<PsiClass> visitedClasses) {
    for (PsiClassType superType : supers) {
      PsiClass aSuper = superType.resolve();

      if (aSuper != null) {
        Pair<T, InheritDocProvider<T>> tag = searchDocTagInOverridenMethod(method, aSuper, loc);

        if (tag != null) return tag;
      }
    }

    for (PsiClassType superType : supers) {
      PsiClass aSuper = superType.resolve();

      if (aSuper != null && visitedClasses.add(aSuper)) {
        Pair<T, InheritDocProvider<T>> tag = findInheritDocTagInClass(method, aSuper, loc, visitedClasses);

        if (tag != null) {
          return tag;
        }
      }
    }

    return null;
  }

  private <T> Pair<T, InheritDocProvider<T>> findInheritDocTagInClass(PsiMethod aMethod,
                                                                      PsiClass aClass,
                                                                      DocTagLocator<T> loc,
                                                                      Set<PsiClass> visitedClasses) {
    if (aClass == null) {
      return null;
    }

    Pair<T, InheritDocProvider<T>> delegate = findInheritDocTagInDelegate(aMethod, loc);
    if (delegate != null) {
      return delegate;
    }

    if (aClass instanceof PsiAnonymousClass) {
      return searchDocTagInSupers(new PsiClassType[]{((PsiAnonymousClass)aClass).getBaseClassType()}, aMethod, loc, visitedClasses);
    }

    PsiClassType[] implementsTypes = aClass.getImplementsListTypes();
    Pair<T, InheritDocProvider<T>> tag = searchDocTagInSupers(implementsTypes, aMethod, loc, visitedClasses);

    if (tag != null) {
      return tag;
    }

    PsiClassType[] extendsTypes = aClass.getExtendsListTypes();
    return searchDocTagInSupers(extendsTypes, aMethod, loc, visitedClasses);
  }

  @Nullable
  private <T> Pair<T, InheritDocProvider<T>> findInheritDocTagInDelegate(PsiMethod method, DocTagLocator<T> loc) {
    PsiMethod delegateMethod = findDelegateMethod(method);
    if (delegateMethod == null) return null;

    PsiClass containingClass = delegateMethod.getContainingClass();
    if (containingClass == null) return null;

    T tag = loc.find(delegateMethod, getDocComment(delegateMethod));
    if (tag == null) return null;

    return Pair.create(tag, new InheritDocProvider<T>() {
      @Override
      public Pair<T, InheritDocProvider<T>> getInheritDoc() {
        return findInheritDocTag(delegateMethod, loc);
      }

      @Override
      public PsiClass getElement() {
        return containingClass;
      }
    });
  }

  @Nullable
  private static PsiMethod findDelegateMethod(@NotNull PsiMethod method) {
    PsiDocCommentOwner delegate = DocumentationDelegateProvider.findDocumentationDelegate(method);
    return delegate instanceof PsiMethod ? (PsiMethod)delegate : null;
  }

  @Nullable
  private <T> Pair<T, InheritDocProvider<T>> findInheritDocTag(PsiMethod method, DocTagLocator<T> loc) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;

    return findInheritDocTagInClass(method, aClass, loc, new HashSet<>());
  }

  private static class ParamInfo {
    private final String name;
    private final PsiDocTag docTag;
    private final InheritDocProvider<PsiDocTag> inheritDocTagProvider;

    private ParamInfo(String paramName, PsiDocTag tag, InheritDocProvider<PsiDocTag> provider) {
      name = paramName;
      docTag = tag;
      inheritDocTagProvider = provider;
    }
    
    private ParamInfo(String paramName, @NotNull Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> tagWithInheritProvider) {
      this(paramName, tagWithInheritProvider.first, tagWithInheritProvider.second);
    }
  }

  private static class ReturnTagLocator implements DocTagLocator<PsiDocTag> {
    @Override
    public PsiDocTag find(PsiDocCommentOwner owner, PsiDocComment comment) {
      if (comment == null) {
        return null;
      }

      return comment.findTagByName("return");
    }
  }
  
  private static class MyVisitor extends JavaElementVisitor {

    @NotNull private final StringBuilder myBuffer;

    MyVisitor(@NotNull StringBuilder buffer) {
      myBuffer = buffer;
    }

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      myBuffer.append("new ");
      PsiType type = expression.getType();
      if (type != null) {
        generateType(myBuffer, type, expression);
      }
      expression.acceptChildren(this);
    }

    @Override
    public void visitExpressionList(PsiExpressionList list) {
      myBuffer.append("(");
      String separator = ", ";
      PsiExpression[] expressions = list.getExpressions();
      for (PsiExpression expression : expressions) {
        expression.accept(this);
        myBuffer.append(separator);
      }
      if (expressions.length > 0) {
        myBuffer.setLength(myBuffer.length() - separator.length());
      }
      myBuffer.append(")");
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      myBuffer.append(StringUtil.escapeXml(expression.getMethodExpression().getText()));
      expression.getArgumentList().accept(this);
    }

    @Override
    public void visitExpression(PsiExpression expression) {
      myBuffer.append(StringUtil.escapeXml(expression.getText()));
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      myBuffer.append(StringUtil.escapeXml(expression.getText()));
    }
  }
}
