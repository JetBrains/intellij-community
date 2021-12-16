// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.javadoc;

import com.intellij.CommonBundle;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.documentation.DocumentationManagerProtocol;
import com.intellij.codeInsight.documentation.DocumentationManagerUtil;
import com.intellij.java.JavaBundle;
import com.intellij.javadoc.JavadocGeneratorRunProfile;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.lang.documentation.DocumentationSettings;
import com.intellij.lang.documentation.DocumentationSettings.InlineCodeHighlightingMode;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import kotlin.text.StringsKt;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class JavaDocInfoGenerator {
  private static final Logger LOG = Logger.getInstance(JavaDocInfoGenerator.class);

  private interface InheritDocProvider<T> {
    Pair<T, InheritDocProvider<T>> getInheritDoc();

    PsiClass getElement();
  }

  @FunctionalInterface
  private interface DocTagLocator<T> {
    T find(PsiDocCommentOwner owner, PsiDocComment comment);
  }

  private static final String BR_TAG = "<br>";
  private static final String LINK_TAG = "link";
  private static final String LITERAL_TAG = "literal";
  private static final String CODE_TAG = "code";
  private static final String SYSTEM_PROPERTY_TAG = "systemProperty";
  private static final String LINKPLAIN_TAG = "linkplain";
  private static final String INHERIT_DOC_TAG = "inheritDoc";
  private static final String DOC_ROOT_TAG = "docRoot";
  private static final String VALUE_TAG = "value";
  private static final String LT = "&lt;";
  private static final String GT = "&gt;";
  private static final String NBSP = "&nbsp;";

  private static final Pattern ourWhitespaces = Pattern.compile("[ \\n\\r\\t]+");
  private static final Pattern ourRelativeHtmlLinks = Pattern.compile("<A.*?HREF=\"([^\":]*)\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  private static final InheritDocProvider<PsiDocTag> ourEmptyProvider = new InheritDocProvider<>() {
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

  private final @NotNull Project myProject;
  private final @Nullable PsiElement myElement;
  private final @NotNull JavaDocHighlightingManager myHighlightingManager;
  private final @Nullable JavaSdkVersion mySdkVersion;

  private final boolean myIsRendered;
  private final boolean myDoHighlightSignatures;
  private final boolean myDoHighlightCodeBlocks;
  private final InlineCodeHighlightingMode myInlineCodeBlocksHighlightingMode;
  private final boolean myDoSemanticHighlightingOfLinks;
  private final float myHighlightingSaturation;

  private boolean myIsSignatureGenerationInProgress;

  public JavaDocInfoGenerator(@NotNull Project project, @Nullable PsiElement element) {
    this(
      project,
      element,
      new JavaDocHighlightingManagerImpl(),
      false,
      true,
      true,
      InlineCodeHighlightingMode.AS_DEFAULT_CODE,
      false,
      1.0F);
  }

  public JavaDocInfoGenerator(
    @NotNull Project project,
    @Nullable PsiElement element,
    @NotNull JavaDocHighlightingManager highlightingManager,
    boolean isGenerationForRenderedDoc,
    boolean doHighlightSignatures,
    boolean doHighlightCodeBlocks,
    @NotNull InlineCodeHighlightingMode inlineCodeBlocksHighlightingMode,
    boolean doSemanticHighlightingOfLinks,
    float highlightingSaturationFactor
  ) {
    myProject = project;
    myElement = element;
    myIsRendered = isGenerationForRenderedDoc;
    myHighlightingManager = highlightingManager;
    myDoHighlightSignatures = doHighlightSignatures;
    myDoHighlightCodeBlocks = doHighlightCodeBlocks;
    myInlineCodeBlocksHighlightingMode = inlineCodeBlocksHighlightingMode;
    myDoSemanticHighlightingOfLinks = doSemanticHighlightingOfLinks;
    myHighlightingSaturation = highlightingSaturationFactor;

    Sdk jdk = JavadocGeneratorRunProfile.getSdk(myProject);
    mySdkVersion = jdk == null ? null : JavaSdk.getInstance().getVersion(jdk);
  }

  public boolean isRendered() {
    return myIsRendered;
  }

  public boolean doHighlightSignatures() {
    return myDoHighlightSignatures;
  }

  public boolean doHighlightCodeBlocks() {
    return myDoHighlightCodeBlocks;
  }

  public @NotNull InlineCodeHighlightingMode getInlineCodeHighlightingMode() {
    return myInlineCodeBlocksHighlightingMode;
  }

  public boolean doSemanticHighlightingOfLinks() {
    return myDoSemanticHighlightingOfLinks;
  }

  public float getHighlightingSaturation() {
    return myHighlightingSaturation;
  }

  public @NotNull JavaDocHighlightingManager getHighlightingManager() {
    return myHighlightingManager;
  }

  protected @NotNull StringBuilder appendStyledSpan(
    boolean doHighlighting,
    @NotNull StringBuilder buffer,
    @NotNull TextAttributes attributes,
    @Nullable String value
  ) {
    if (doHighlighting) {
      HtmlSyntaxInfoUtil.appendStyledSpan(buffer, attributes, value, getHighlightingSaturation());
    }
    else {
      buffer.append(value);
    }
    return buffer;
  }

  protected @NotNull StringBuilder appendStyledSpan(
    @NotNull StringBuilder buffer,
    @NotNull TextAttributes attributes,
    @Nullable String value
  ) {
    return appendStyledSpan(doHighlightSignatures(), buffer, attributes, value);
  }

  protected @NotNull StringBuilder appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
    boolean doHighlighting,
    @NotNull StringBuilder buffer,
    @NotNull Project project,
    @NotNull Language language,
    @Nullable String codeSnippet
  ) {
    if (doHighlighting) {
      HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
        buffer, project, language, codeSnippet, getHighlightingSaturation());
    }
    else if (codeSnippet != null) {
      codeSnippet = StringsKt.trimIndent(codeSnippet);
      codeSnippet = StringUtil.escapeXmlEntities(codeSnippet);
      codeSnippet = codeSnippet.replace("\n", BR_TAG);
      buffer.append(codeSnippet);
    }
    return buffer;
  }

  protected @NotNull String getStyledSpan(boolean doHighlighting, @NotNull TextAttributes attributes, @Nullable String value) {
    return appendStyledSpan(doHighlighting, new StringBuilder(), attributes, value).toString();
  }

  public @NotNull String getHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
    @NotNull Project project,
    @NotNull Language language,
    @Nullable String codeSnippet
  ) {
    return appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(true, new StringBuilder(), project, language, codeSnippet).toString();
  }

  private static InheritDocProvider<PsiElement[]> mapProvider(InheritDocProvider<PsiDocTag> i, boolean dropFirst) {
    return new InheritDocProvider<>() {
      @Override
      public Pair<PsiElement[], InheritDocProvider<PsiElement[]>> getInheritDoc() {
        Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> pair = i.getInheritDoc();
        if (pair == null) return null;

        PsiElement[] elements;
        PsiElement[] rawElements = pair.first.getDataElements();
        if (dropFirst && rawElements.length > 0) {
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

  private static DocTagLocator<PsiDocTag> parameterLocator(final int parameterIndex) {
    return (owner, comment) -> {
      if (parameterIndex < 0 || comment == null || !(owner instanceof PsiMethod)) return null;

      PsiParameter[] parameters = ((PsiMethod)owner).getParameterList().getParameters();
      if (parameterIndex >= parameters.length) return null;

      String name = parameters[parameterIndex].getName();
      return getParamTagByName(comment, name);
    };
  }

  private static DocTagLocator<PsiDocTag> typeParameterLocator(final int parameterIndex) {
    return (owner, comment) -> {
      if (parameterIndex < 0 || comment == null || !(owner instanceof PsiTypeParameterListOwner)) return null;

      PsiTypeParameter[] parameters = ((PsiTypeParameterListOwner)owner).getTypeParameters();
      if (parameterIndex >= parameters.length) return null;

      String rawName = parameters[parameterIndex].getName();
      if (rawName == null) return null;
      String name = '<' + rawName + '>';
      return getParamTagByName(comment, name);
    };
  }

  private static PsiDocTag getParamTagByName(@NotNull PsiDocComment comment, String name) {
    PsiDocTag[] tags = comment.findTagsByName("param");
    return getTagByName(tags, name);
  }

  private static PsiDocTag getTagByName(PsiDocTag @NotNull [] tags, String name) {
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

  private static DocTagLocator<PsiDocTag> exceptionLocator(String name) {
    return (owner, comment) -> {
      if (comment == null) return null;

      for (PsiDocTag tag : getThrowsTags(comment)) {
        PsiDocTagValue value = tag.getValueElement();
        if (value != null) {
          String text = value.getText();
          if (text != null && areWeakEqual(text, name)) {
            return tag;
          }
        }
      }

      return null;
    };
  }

  private @Nls String sanitizeHtml(@Nls StringBuilder buffer) {
    String text = buffer.toString();
    if (text.isEmpty()) return null;

    if (myElement != null) {  // PSI element refs can't be resolved without a context
      StringBuilder result = new StringBuilder();
      int lastRef = 0;
      Matcher matcher = ourRelativeHtmlLinks.matcher(text);
      while (matcher.find()) {
        int groupStart = matcher.start(1), groupEnd = matcher.end(1);
        result.append(text, lastRef, groupStart);
        String href = text.substring(groupStart, groupEnd);
        String reference = "";
        try {
          reference = createReferenceForRelativeLink(href, myElement);
        }
        catch (IndexNotReadyException e) {
          LOG.debug(e);
          result.replace(result.length() - 6, result.length(), "wrong-href=\""); // display text instead of link
        }
        result.append(ObjectUtils.notNull(reference, href));
        lastRef = groupEnd;
      }
      if (lastRef > 0) {  // don't copy text over if there are no matches
        result.append(text, lastRef, text.length());
        text = result.toString(); //NON-NLS
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Generated JavaDoc:");
      LOG.debug(text);
    }

    text = StringUtil.replaceIgnoreCase(text, "<p/>", "<p></p>"); //NON-NLS
    text = StringUtil.replace(text, "/>", ">");
    return text;
  }

  /**
   * Converts a relative link into {@link DocumentationManagerProtocol#PSI_ELEMENT_PROTOCOL PSI_ELEMENT_PROTOCOL}-type link if possible.
   */
  @Nullable
  static String createReferenceForRelativeLink(@NotNull String relativeLink, @NotNull PsiElement contextElement) {
    String fragment = null;
    int hashPosition = relativeLink.indexOf('#');
    if (hashPosition >= 0) {
      fragment = relativeLink.substring(hashPosition + 1);
      relativeLink = relativeLink.substring(0, hashPosition);
    }

    PsiElement targetElement;
    if (relativeLink.isEmpty()) {
      targetElement = contextElement instanceof PsiField || contextElement instanceof PsiMethod ?
                      ((PsiMember)contextElement).getContainingClass() : contextElement;
    }
    else {
      if (!(StringUtil.endsWithIgnoreCase(relativeLink, ".htm") || StringUtil.endsWithIgnoreCase(relativeLink, ".html"))) {
        return null;
      }
      relativeLink = relativeLink.substring(0, relativeLink.lastIndexOf('.'));

      String packageName = getPackageName(contextElement);
      if (packageName == null) return null;

      Couple<String> pathWithPackage = removeParentReferences(relativeLink, packageName);
      if (pathWithPackage == null) return null;
      relativeLink = pathWithPackage.first;
      packageName = pathWithPackage.second;

      relativeLink = relativeLink.replace('/', '.');

      String qualifiedTargetName = packageName.isEmpty() ? relativeLink : packageName + '.' + relativeLink;
      JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(contextElement.getProject());
      targetElement = "package-summary".equals(StringUtil.getShortName(qualifiedTargetName))
                      ? javaPsiFacade.findPackage(StringUtil.getPackageName(qualifiedTargetName))
                      : javaPsiFacade.findClass(qualifiedTargetName, contextElement.getResolveScope());
    }
    if (targetElement == null) return null;

    if (fragment != null && targetElement instanceof PsiClass) {
      if (fragment.indexOf('-') >= 0 || fragment.indexOf('(') >= 0) {
        for (PsiMethod method : ((PsiClass)targetElement).getMethods()) {
          Set<String> signatures = JavaDocumentationProvider.getHtmlMethodSignatures(method, null);
          if (signatures.contains(fragment)) {
            targetElement = method;
            fragment = null;
            break;
          }
        }
      }
      else {
        for (PsiField field : ((PsiClass)targetElement).getFields()) {
          if (fragment.equals(field.getName())) {
            targetElement = field;
            fragment = null;
            break;
          }
        }
      }
    }

    StringBuilder builder = new StringBuilder();
    builder.append(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL);
    builder.append(JavaDocUtil.getReferenceText(targetElement.getProject(), targetElement));
    if (fragment != null) {
      builder.append(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL_REF_SEPARATOR).append(fragment);
    }
    return builder.toString();
  }

  /**
   * Takes a pair of strings representing a relative path and a package name, and returns corresponding pair, where path is stripped of
   * leading ../ elements, and package name adjusted correspondingly. Returns {@code null} if there are more ../ elements than package
   * components.
   */
  @Nullable
  private static Couple<String> removeParentReferences(String path, String packageName) {
    while (path.startsWith("../")) {
      if (packageName.isEmpty()) return null;
      int dotPos = packageName.lastIndexOf('.');
      packageName = dotPos < 0 ? "" : packageName.substring(0, dotPos);
      path = path.substring(3);
    }
    return Couple.of(path, packageName);
  }

  private static String getPackageName(PsiElement element) {
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

  public boolean generateDocInfoCore(StringBuilder buffer, boolean generatePrologue) {
    if (myElement instanceof PsiClass) {
      generateClassJavaDoc(buffer, (PsiClass)myElement, generatePrologue);
    }
    else if (myElement instanceof PsiMethod) {
      generateMethodJavaDoc(buffer, (PsiMethod)myElement, generatePrologue);
    }
    else if (myElement instanceof PsiParameter) {
      generateMethodParameterJavaDoc(buffer, (PsiParameter)myElement, generatePrologue);
    }
    else if (myElement instanceof PsiField) {
      generateFieldJavaDoc(buffer, (PsiField)myElement, generatePrologue);
    }
    else if (myElement instanceof PsiVariable) {
      generateVariableJavaDoc(buffer, (PsiVariable)myElement, generatePrologue);
    }
    else if (myElement instanceof PsiDirectory) {
      PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)myElement);
      if (aPackage == null) return false;
      generatePackageJavaDoc(buffer, aPackage, generatePrologue);
    }
    else if (myElement instanceof PsiPackage) {
      generatePackageJavaDoc(buffer, (PsiPackage)myElement, generatePrologue);
    }
    else if (myElement instanceof PsiJavaModule) {
      generateModuleJavaDoc(buffer, (PsiJavaModule)myElement, generatePrologue);
    }
    else {
      return false;
    }

    return true;
  }

  public @NlsSafe String generateSignature(PsiElement element) {
    StringBuilder buf = new StringBuilder();
    if (element instanceof PsiClass) {
      if (generateClassSignature(buf, (PsiClass)element, SignaturePlace.ToolTip)) return null;
    }
    else if (element instanceof PsiField) {
      generateFieldSignature(buf, (PsiField)element, SignaturePlace.ToolTip);
    }
    else if (element instanceof PsiMethod) {
      generateMethodSignature(buf, (PsiMethod)element, SignaturePlace.ToolTip);
    }
    return buf.toString();
  }

  public @Nls @Nullable String generateDocInfo(List<@NlsSafe String> docURLs) {
    @Nls StringBuilder buffer = new StringBuilder();

    if (!generateDocInfoCore(buffer, true)) {
      return null;
    }

    HtmlChunk containerInfo = generateContainerInfo(myElement);
    if (containerInfo != null) {
      containerInfo.appendTo(buffer);
    }

    if (docURLs != null) {
      if (buffer.length() > 0 && elementHasSourceCode()) {
        LOG.debug("Documentation for " + myElement + " was generated from source code, it wasn't found at following URLs: ", docURLs);
      }
      else {
        HtmlChunk urlList = DocumentationMarkup.GRAYED_ELEMENT
          .addText(JavaBundle.message("javadoc.documentation.url.checked", docURLs.size()))
          .children(ContainerUtil.map(docURLs, url -> new HtmlBuilder().br().nbsp().append(url).toFragment()));
        HtmlChunk settingsLink = HtmlChunk.link("open://Project Settings", JavaBundle.message("javadoc.edit.api.docs.paths"));
        buffer.append(DocumentationMarkup.CONTENT_ELEMENT.child(
          DocumentationMarkup.CENTERED_ELEMENT.children(urlList, HtmlChunk.br(), settingsLink)));
      }
    }

    return sanitizeHtml(buffer);
  }

  public @Nls @Nullable String generateRenderedDocInfo() {
    StringBuilder buffer = new StringBuilder();

    if (myElement instanceof PsiClass) {
      generateClassJavaDoc(buffer, (PsiClass)myElement, true);
    }
    else if (myElement instanceof PsiMethod) {
      generateMethodJavaDoc(buffer, (PsiMethod)myElement, true);
    }
    else if (myElement instanceof PsiField) {
      generateFieldJavaDoc(buffer, (PsiField)myElement, true);
    }
    else if (myElement instanceof PsiJavaModule) {
      generateModuleJavaDoc(buffer, (PsiJavaModule)myElement, true);
    }
    else if (myElement instanceof PsiDocComment) { // package-info case
      generatePackageJavaDoc(buffer, (PsiDocComment)myElement, true);
    }
    else {
      return null;
    }

    return sanitizeHtml(buffer);
  }

  private @Nullable HtmlChunk generateContainerInfo(@Nullable PsiElement element) {
    JavaDocHighlightingManager highlightingManager = JavaDocHighlightingManagerImpl.getInstance();

    @NlsSafe String ownerLink = null;
    String ownerIcon = null;

    if (element instanceof PsiClass) {
      PsiFile file = element.getContainingFile();
      if (file instanceof PsiJavaFile) {
        String packageName = ((PsiJavaFile)file).getPackageName();
        if (!packageName.isEmpty()) {
          PsiPackage aPackage = JavaPsiFacade.getInstance(file.getProject()).findPackage(packageName);
          StringBuilder packageFqnBuilder = new StringBuilder();
          if (DocumentationSettings.isSemanticHighlightingOfLinksEnabled()) {
            appendStyledSpan(packageFqnBuilder, highlightingManager.getClassNameAttributes(), packageName);
          }
          else {
            packageFqnBuilder.append(packageName);
          }
          ownerLink = aPackage != null
                      ? generateLink(aPackage, packageFqnBuilder.toString(), false, false)
                      : "<code>" + packageFqnBuilder + "</code>";
          ownerIcon = "AllIcons.Nodes.Package";
        }
      }
    }
    else if (element instanceof PsiMember) {
      PsiClass parentClass = ((PsiMember)element).getContainingClass();
      if (parentClass != null && !PsiUtil.isArrayClass(parentClass)) {
        String qName = parentClass.getQualifiedName();
        if (qName != null) {
          StringBuilder classFqnBuilder = new StringBuilder();
          if (DocumentationSettings.isSemanticHighlightingOfLinksEnabled()) {
            appendStyledSpan(classFqnBuilder, highlightingManager.getClassNameAttributes(), qName);
          }
          else {
            classFqnBuilder.append(qName);
          }
          classFqnBuilder.append(generateTypeParameters(parentClass, false));
          ownerLink = generateLink(parentClass, classFqnBuilder.toString(), false, false);
          ownerIcon = "AllIcons.Nodes.Class";
        }
      }
    }

    if (ownerLink != null) {
      return HtmlChunk.div()
        .setClass("bottom")
        .children(
          HtmlChunk.tag("icon").attr("src", ownerIcon),
          HtmlChunk.nbsp(),
          HtmlChunk.raw(ownerLink)
        );
    }
    return null;
  }

  private boolean elementHasSourceCode() {
    PsiFileSystemItem[] items;
    if (myElement instanceof PsiDirectory) {
      final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)myElement);
      if (aPackage == null) return false;
      items = aPackage.getDirectories(GlobalSearchScope.everythingScope(myProject));
    }
    else if (myElement instanceof PsiPackage) {
      items = ((PsiPackage)myElement).getDirectories(GlobalSearchScope.everythingScope(myProject));
    }
    else {
      PsiFile containingFile = myElement.getNavigationElement().getContainingFile();
      if (containingFile == null) return false;
      items = new PsiFileSystemItem[]{containingFile};
    }
    ProjectFileIndex projectFileIndex = ProjectFileIndex.SERVICE.getInstance(myProject);
    for (PsiFileSystemItem item : items) {
      VirtualFile file = item.getVirtualFile();
      if (file != null && projectFileIndex.isInSource(file)) return true;
    }
    return false;
  }

  private void generateClassJavaDoc(StringBuilder buffer, PsiClass aClass, boolean generatePrologue) {
    if (aClass instanceof PsiAnonymousClass) return;

    if (generatePrologue) generatePrologue(buffer);

    if (!isRendered()) {
      buffer.append(DocumentationMarkup.DEFINITION_START);
      myIsSignatureGenerationInProgress = true;
      if (generateClassSignature(buffer, aClass, SignaturePlace.Javadoc)) {
        myIsSignatureGenerationInProgress = false;
        return;
      }
      myIsSignatureGenerationInProgress = false;
      buffer.append(DocumentationMarkup.DEFINITION_END);
    }

    PsiDocComment comment = getDocComment(aClass);
    if (comment != null) {
      generateCommonSection(buffer, comment);
      if (isRendered()) {
        generateAuthorAndVersionSections(buffer, comment);
      }
      generateRecordParametersSection(buffer, aClass, comment);
      generateTypeParametersSection(buffer, aClass);
    }
    else {
      buffer.append(DocumentationMarkup.SECTIONS_START);
    }

    if (!isRendered()) {
      new NonCodeAnnotationGenerator(aClass, buffer).explainAnnotations(isRendered(), doHighlightSignatures());
    }
    buffer.append(DocumentationMarkup.SECTIONS_END);
  }

  private void generateRecordParametersSection(StringBuilder buffer, PsiClass recordClass, PsiDocComment comment) {
    if (!recordClass.isRecord() || comment == null) return;
    PsiDocTag[] localTags = comment.findTagsByName("param");
    List<ParamInfo> collectedTags = new ArrayList<>();
    for (PsiRecordComponent component : recordClass.getRecordComponents()) {
      PsiDocTag localTag = getTagByName(localTags, component.getName());
      String presentableName = generateOneParameterPresentableName(component);
      if (localTag != null) {
        collectedTags.add(new ParamInfo(component.getName(), presentableName, localTag, ourEmptyProvider));
      }
    }
    generateParametersSection(buffer, CodeInsightBundle.message("javadoc.parameters"), collectedTags);
  }

  private boolean generateClassSignature(StringBuilder buffer, PsiClass aClass, SignaturePlace place) {
    boolean generateLink = place == SignaturePlace.Javadoc;
    String classKeyword =
      aClass.isInterface() ? PsiKeyword.INTERFACE :
      aClass.isEnum() ? PsiKeyword.ENUM :
      aClass.isRecord() ? PsiKeyword.RECORD : PsiKeyword.CLASS;

    generateAnnotations(buffer, aClass, place, true, false, true);
    generateModifiers(buffer, aClass, false);
    if (aClass.isAnnotationType()) {
      buffer.append("@");
    }
    appendStyledSpan(buffer, getHighlightingManager().getKeywordAttributes(), classKeyword);
    buffer.append(' ');
    String refText = JavaDocUtil.getReferenceText(aClass.getProject(), aClass);
    if (refText == null) {
      buffer.setLength(0);
      return true;
    }
    String className = JavaDocUtil.getLabelText(aClass.getProject(), aClass.getManager(), refText, aClass);
    appendStyledSpan(buffer, getHighlightingManager().getClassDeclarationAttributes(aClass), className);

    buffer.append(generateTypeParameters(aClass, false));

    buffer.append('\n');

    PsiClassType[] refs = aClass.getExtendsListTypes();
    if (aClass.isEnum()) {
      refs = Arrays.stream(refs)
        .filter(it -> {
          PsiClass resolved = it.resolve();
          return resolved == null || !"java.lang.Enum".equals(resolved.getQualifiedName());
        })
        .toArray(PsiClassType[]::new);
    }
    if (refs.length > 0) {
      generateRefList(buffer, aClass, generateLink, refs, "extends");
      buffer.append('\n');
    }

    refs = aClass.getImplementsListTypes();
    if (refs.length > 0) {
      generateRefList(buffer, aClass, generateLink, refs, "implements");
      buffer.append('\n');
    }
    if (buffer.charAt(buffer.length() - 1) == '\n') {
      buffer.setLength(buffer.length() - 1);
    }
    return false;
  }

  private void generateRefList(StringBuilder buffer, PsiClass aClass, boolean generateLink, PsiClassType[] refs, String keyword) {
    appendStyledSpan(buffer, getHighlightingManager().getKeywordAttributes(), keyword);
    buffer.append(" ");
    for (int i = 0; i < refs.length; i++) {
      generateType(buffer, refs[i], aClass, generateLink);
      if (i < refs.length - 1) {
        appendStyledSpan(buffer, getHighlightingManager().getCommaAttributes(), ",");
        if (refs.length <= 3) {
          buffer.append(NBSP);
        }
        else {
          buffer.append('\n');
          buffer.append(NBSP.repeat(keyword.length() + 1));
        }
      }
    }
  }

  private void generateTypeParametersSection(StringBuilder buffer, PsiClass aClass) {
    List<ParamInfo> result = new ArrayList<>();
    PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
    for (int i = 0; i < typeParameters.length; i++) {
      PsiTypeParameter typeParameter = typeParameters[i];
      String presentableParamName = generateOneTypeParameterPresentableName(typeParameter);
      DocTagLocator<PsiDocTag> locator = typeParameterLocator(i);
      Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> inClassComment = findInClassComment(aClass, locator);
      if (inClassComment != null) {
        result.add(new ParamInfo(typeParameter.getName(), presentableParamName, inClassComment));
      }
      else if (!isRendered()) {
        Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> inHierarchy = findInHierarchy(aClass, locator);
        if (inHierarchy != null) {
          result.add(new ParamInfo(typeParameter.getName(), presentableParamName, inHierarchy));
        }
      }
    }
    generateParametersSection(buffer, JavaBundle.message("javadoc.type.parameters"), result);
  }

  @NotNull
  private String generateOneParameterPresentableName(PsiNamedElement parameter) {
    StringBuilder paramName = new StringBuilder();
    appendStyledSpan(
      paramName,
      getHighlightingManager().getParameterAttributes(),
      Objects.requireNonNullElse(parameter.getName(), CommonBundle.getErrorTitle()));
    return paramName.toString();
  }

  @NotNull
  private String generateOneTypeParameterPresentableName(PsiTypeParameter typeParameter) {
    StringBuilder paramName = new StringBuilder();
    paramName.append(LT);
    appendStyledSpan(
      paramName,
      getHighlightingManager().getTypeParameterNameAttributes(),
      Objects.requireNonNullElse(typeParameter.getName(), CommonBundle.getErrorTitle()));
    paramName.append(GT);
    return paramName.toString();
  }

  @Nullable
  private Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> findInHierarchy(PsiClass psiClass, DocTagLocator<PsiDocTag> locator) {
    for (PsiClass superClass : psiClass.getSupers()) {
      Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> pair = findInClassComment(superClass, locator);
      if (pair != null) return pair;
    }
    for (PsiClass superInterface : psiClass.getInterfaces()) {
      Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> pair = findInClassComment(superInterface, locator);
      if (pair != null) return pair;
    }
    return null;
  }

  @Nullable
  private Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> findInClassComment(PsiClass psiClass, DocTagLocator<PsiDocTag> locator) {
    PsiDocTag tag = locator.find(psiClass, getDocComment(psiClass));
    if (tag != null) {
      return new Pair<>(tag, new InheritDocProvider<>() {
        @Override
        public Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> getInheritDoc() {
          return isRendered() ? null : findInHierarchy(psiClass, locator);
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
  private static PsiDocComment getDocComment(PsiJavaDocumentedElement docOwner) {
    PsiElement navElement = docOwner.getNavigationElement();
    if (!(navElement instanceof PsiJavaDocumentedElement)) {
      LOG.info("Wrong navElement: " + navElement + "; original = " + docOwner + " of class " + docOwner.getClass());
      return null;
    }
    PsiDocComment comment = ((PsiJavaDocumentedElement)navElement).getDocComment();
    if (comment == null) { //check for non-normalized fields
      PsiModifierList modifierList = docOwner instanceof PsiDocCommentOwner ? ((PsiDocCommentOwner)docOwner).getModifierList() : null;
      if (modifierList != null) {
        PsiElement parent = modifierList.getParent();
        if (parent instanceof PsiDocCommentOwner && parent.getNavigationElement() instanceof PsiDocCommentOwner) {
          return ((PsiDocCommentOwner)parent.getNavigationElement()).getDocComment();
        }
      }
    }
    return comment;
  }

  private void generateFieldJavaDoc(StringBuilder buffer, PsiField field, boolean generatePrologue) {
    if (generatePrologue) generatePrologue(buffer);

    if (!isRendered()) {
      buffer.append(DocumentationMarkup.DEFINITION_START);
      myIsSignatureGenerationInProgress = true;
      generateFieldSignature(buffer, field, SignaturePlace.Javadoc);
      myIsSignatureGenerationInProgress = false;
      enumConstantOrdinal(buffer, field, field.getContainingClass(), "\n");
      buffer.append(DocumentationMarkup.DEFINITION_END);
    }

    PsiDocComment comment = getDocComment(field);
    if (comment != null) {
      generateCommonSection(buffer, comment);
      if (isRendered()) {
        generateAuthorAndVersionSections(buffer, comment);
      }
    }
    else {
      buffer.append(DocumentationMarkup.SECTIONS_START);
    }

    if (!isRendered()) {
      JavaDocColorUtil.appendColorPreview(field, buffer);
      new NonCodeAnnotationGenerator(field, buffer).explainAnnotations(isRendered(), doHighlightSignatures());
    }

    buffer.append(DocumentationMarkup.SECTIONS_END);
  }

  private void generateFieldSignature(StringBuilder buffer, PsiField field, SignaturePlace place) {
    StringBuilder signatureBuffer = new StringBuilder();
    generateAnnotations(signatureBuffer, field, place, true, false, true);
    generateModifiers(signatureBuffer, field, false);
    generateType(signatureBuffer, field.getType(), field, place == SignaturePlace.Javadoc);
    signatureBuffer.append(" ");
    appendStyledSpan(signatureBuffer, getHighlightingManager().getFieldDeclarationAttributes(field), field.getName());
    buffer.append(signatureBuffer);
    appendInitializer(buffer, field, StringUtil.removeHtmlTags(signatureBuffer.toString()).length());
  }

  public static void enumConstantOrdinal(@Nls StringBuilder buffer, PsiField field, PsiClass parentClass, String newLine) {
    if (parentClass != null && field instanceof PsiEnumConstant) {
      PsiField[] fields = parentClass.getFields();
      int idx = ArrayUtilRt.find(fields, field);
      if (idx >= 0) {
        buffer.append(newLine);
        buffer.append(DocumentationMarkup.GRAYED_START);
        buffer.append("// ");
        buffer.append(JavaBundle.message("enum.constant.ordinal"));
        buffer.append(idx);
        buffer.append(DocumentationMarkup.GRAYED_END);
      }
    }
  }

  // not a javadoc in fact.
  private void generateVariableJavaDoc(StringBuilder buffer, PsiVariable variable, boolean generatePrologue) {
    if (generatePrologue) generatePrologue(buffer);

    generateVariableDefinition(buffer, variable, false);

    buffer.append(DocumentationMarkup.SECTIONS_START);
    JavaDocColorUtil.appendColorPreview(variable, buffer);
    buffer.append(DocumentationMarkup.SECTIONS_END);
  }

  private void generatePackageJavaDoc(StringBuilder buffer, PsiPackage psiPackage, boolean generatePrologue) {
    for (PsiDirectory directory : psiPackage.getDirectories(GlobalSearchScope.everythingScope(myProject))) {
      PsiFile packageInfoFile = directory.findFile(PsiPackage.PACKAGE_INFO_FILE);
      if (packageInfoFile != null) {
        ASTNode node = packageInfoFile.getNode();
        if (node != null) {
          ASTNode docCommentNode = findRelevantCommentNode(node);
          if (docCommentNode != null) {
            generatePackageJavaDoc(buffer, (PsiDocComment)docCommentNode.getPsi(), generatePrologue);
            break;
          }
        }
      }
      PsiFile packageHtmlFile = directory.findFile("package.html");
      if (packageHtmlFile != null) {
        generatePackageHtmlJavaDoc(buffer, packageHtmlFile, generatePrologue);
        break;
      }
    }
  }

  private void generatePackageJavaDoc(StringBuilder buffer, PsiDocComment comment, boolean generatePrologue) {
    if (generatePrologue) generatePrologue(buffer);
    generateCommonSection(buffer, comment);
    buffer.append(DocumentationMarkup.SECTIONS_END);
  }

  private void generateModuleJavaDoc(StringBuilder buffer, PsiJavaModule module, boolean generatePrologue) {
    if (generatePrologue) generatePrologue(buffer);

    if (!isRendered()) {
      buffer.append(DocumentationMarkup.DEFINITION_START);
      generateAnnotations(buffer, module, SignaturePlace.Javadoc, true, false, true);
      appendStyledSpan(buffer, getHighlightingManager().getKeywordAttributes(), "module ");
      appendStyledSpan(buffer, getHighlightingManager().getClassNameAttributes(), module.getName());
      buffer.append(DocumentationMarkup.DEFINITION_END);
    }

    PsiDocComment comment = getDocComment(module);
    if (comment != null) {
      generateCommonSection(buffer, comment);
      buffer.append(DocumentationMarkup.SECTIONS_END);
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
    if (!isEmptyDescription(docComment)) {
      buffer.append(DocumentationMarkup.CONTENT_START);
      generateDescription(buffer, docComment);
      buffer.append(DocumentationMarkup.CONTENT_END);
    }

    buffer.append(DocumentationMarkup.SECTIONS_START).append("<p>");
    generateApiSection(buffer, docComment);
    generateDeprecatedSection(buffer, docComment);
    generateSinceSection(buffer, docComment);
    generateSeeAlsoSection(buffer, docComment);
  }

  private void generateAuthorAndVersionSections(StringBuilder buffer, PsiDocComment docComment) {
    generateAuthorSection(buffer, docComment);
    generateSingleTagSection(buffer, docComment, "version", JavaBundle.messagePointer("javadoc.version"));
  }

  private void generateApiSection(StringBuilder buffer, PsiDocComment comment) {
    generateSingleTagSection(buffer, comment, "apiNote", JavaBundle.messagePointer("javadoc.apiNote"));
    generateSingleTagSection(buffer, comment, "implSpec", JavaBundle.messagePointer("javadoc.implSpec"));
    generateSingleTagSection(buffer, comment, "implNote", JavaBundle.messagePointer("javadoc.implNote"));
  }

  private void generatePackageHtmlJavaDoc(StringBuilder buffer, PsiFile packageHtmlFile, boolean generatePrologue) {
    String htmlText = packageHtmlFile.getText();

    try {
      Element rootTag = JDOMUtil.load(htmlText);
      Element subTag = rootTag.getChild("body");
      if (subTag != null) {
        htmlText = subTag.getValue();
      }
    }
    catch (JDOMException | IOException ignore) {
    }

    htmlText = StringUtil.replace(htmlText, "*/", "&#42;&#47;");

    String fileText = "/** " + htmlText + " */";
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(packageHtmlFile.getProject());
    PsiDocComment docComment;
    try {
      docComment = elementFactory.createDocCommentFromText(fileText);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return;
    }

    if (generatePrologue) generatePrologue(buffer);
    generateCommonSection(buffer, docComment);
    buffer.append(DocumentationMarkup.SECTIONS_END);
  }

  @Nullable
  public static PsiExpression calcInitializerExpression(PsiVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    if (initializer != null) {
      PsiModifierList modifierList = variable.getModifierList();
      if (modifierList != null &&
          modifierList.hasModifierProperty(PsiModifier.FINAL) &&
          !(initializer instanceof PsiLiteralExpression || initializer instanceof PsiPrefixExpression)) {
        JavaPsiFacade instance = JavaPsiFacade.getInstance(variable.getProject());
        Object o = instance.getConstantEvaluationHelper().computeConstantExpression(initializer);
        if (o != null) {
          String text = o.toString();
          PsiType type = variable.getType();
          if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            text = '"' + StringUtil.escapeStringCharacters(StringUtil.shortenPathWithEllipsis(text, 120)) + '"';
          }
          else if (type.equalsToText("char")) {
            text = '\'' + text + '\'';
          }
          try {
            return instance.getElementFactory().createExpressionFromText(text, variable);
          }
          catch (IncorrectOperationException ex) {
            LOG.info("type:" + type.getCanonicalText() + "; text: " + text, ex);
          }
        }
      }
    }
    return null;
  }

  public void appendExpressionValue(StringBuilder buffer, PsiExpression initializer) {
    appendStyledSpan(buffer, getHighlightingManager().getOperationSignAttributes(), " = ");
    String text = initializer.getText().trim();
    int index = newLineIndex(text);
    boolean trunc = index < text.length();
    if (trunc) {
      text = text.substring(0, index);
    }
    appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
      doHighlightSignatures(), buffer, initializer.getProject(), initializer.getLanguage(), text);
    if (trunc) {
      buffer.append("...");
    }
  }

  private void appendInitializer(StringBuilder buffer, PsiVariable variable, int variableSignatureLength) {
    PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return;

    String initializerText = initializer.getText().trim();
    if (variableSignatureLength + initializerText.length() < 80) {
      // initializer should be printed on the same line
      buffer.append(" ");
    }
    else {
      // initializer should be printed on the new line
      buffer.append("\n");
      buffer.append(NBSP.repeat(CodeStyle.getIndentSize(variable.getContainingFile())));
    }
    appendStyledSpan(buffer, getHighlightingManager().getOperationSignAttributes(), "= ");

    int index = newLineIndex(initializerText);
    if (index < initializerText.length()) {
      initializerText = initializerText.substring(0, index);
      buffer.append(StringUtil.escapeXmlEntities(initializerText));
      buffer.append("...");
    }
    else {
      initializer.accept(new MyVisitor(buffer));
    }
    PsiExpression constantInitializer = calcInitializerExpression(variable);
    if (constantInitializer != null) {
      buffer.append(DocumentationMarkup.GRAYED_START);
      appendExpressionValue(buffer, constantInitializer);
      buffer.append(DocumentationMarkup.GRAYED_END);
    }
  }

  private static int newLineIndex(String text) {
    int index1 = text.indexOf('\n');
    if (index1 < 0) index1 = text.length();
    int index2 = text.indexOf('\r');
    if (index2 < 0) index2 = text.length();
    return Math.min(index1, index2);
  }

  public int generateModifiers(StringBuilder buffer, PsiModifierListOwner owner, boolean nbsp) {
    String modifiers = PsiFormatUtil.formatModifiers(owner, PsiFormatUtilBase.JAVADOC_MODIFIERS_ONLY);
    if (!modifiers.isEmpty()) {
      appendStyledSpan(buffer, getHighlightingManager().getKeywordAttributes(), modifiers);
      buffer.append(nbsp ? NBSP : ' ');
    }
    return modifiers.length();
  }

  private int generateTypeAnnotations(
    StringBuilder buffer,
    PsiAnnotationOwner owner,
    PsiElement context,
    boolean generateLink,
    boolean leadingSpace
  ) {
    int len = 0;
    List<AnnotationDocGenerator> generators = AnnotationDocGenerator.getAnnotationsToShow(owner, context);
    if (leadingSpace && !generators.isEmpty()) {
      buffer.append(NBSP);
      len++;
    }
    for (AnnotationDocGenerator anno : generators) {
      StringBuilder buf = new StringBuilder();
      anno.generateAnnotation(buf, AnnotationFormat.JavaDocShort, generateLink, isRendered(), doHighlightSignatures());
      len += StringUtil.unescapeXmlEntities(StringUtil.stripHtml(buf.toString(), true)).length() + 1;
      buffer.append(buf);
      buffer.append(NBSP);
    }
    return len;
  }

  private void generateAnnotations(
    StringBuilder buffer,
    PsiModifierListOwner owner,
    SignaturePlace place,
    boolean splitAnnotations,
    boolean ignoreNonSourceAnnotations,
    boolean generateLink
  ) {
    AnnotationFormat format = place == SignaturePlace.Javadoc ? AnnotationFormat.JavaDocShort : AnnotationFormat.ToolTip;
    for (AnnotationDocGenerator anno : AnnotationDocGenerator.getAnnotationsToShow(owner)) {
      if (ignoreNonSourceAnnotations && (anno.isInferred() || anno.isExternal())) continue;
      anno.generateAnnotation(buffer, format, generateLink, isRendered(), doHighlightSignatures());

      buffer.append(NBSP);
      if (splitAnnotations) buffer.append('\n');
    }
  }

  public static boolean isDocumentedAnnotationType(@NotNull PsiClass resolved) {
    return AnnotationUtil.isAnnotated(resolved, "java.lang.annotation.Documented", 0);
  }

  public static boolean isRepeatableAnnotationType(@Nullable PsiElement annotationType) {
    return annotationType instanceof PsiClass &&
           AnnotationUtil.isAnnotated((PsiClass)annotationType, CommonClassNames.JAVA_LANG_ANNOTATION_REPEATABLE, 0);
  }

  private void generateVariableDefinition(StringBuilder buffer, PsiVariable variable, boolean annotations) {
    buffer.append(DocumentationMarkup.DEFINITION_START);

    StringBuilder signatureBuffer = new StringBuilder();
    generateModifiers(signatureBuffer, variable, false);
    if (annotations) {
      generateAnnotations(signatureBuffer, variable, SignaturePlace.Javadoc, true, false, true);
    }
    generateType(signatureBuffer, variable.getType(), variable);
    signatureBuffer.append(" ");
    appendStyledSpan(signatureBuffer, getHighlightingManager().getLocalVariableAttributes(), variable.getName());

    buffer.append(signatureBuffer);

    appendInitializer(buffer, variable, StringUtil.removeHtmlTags(signatureBuffer.toString()).length());

    buffer.append(DocumentationMarkup.DEFINITION_END);
  }

  private void generateMethodParameterJavaDoc(StringBuilder buffer, PsiParameter parameter, boolean generatePrologue) {
    if (generatePrologue) generatePrologue(buffer);

    generateVariableDefinition(buffer, parameter, true);

    PsiElement method = PsiTreeUtil.getParentOfType(parameter, PsiMethod.class, PsiLambdaExpression.class);
    if (method instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)method;
      PsiParameterList parameterList = psiMethod.getParameterList();
      if (parameter.getParent() == parameterList) { // this can also be a parameter in foreach statement or in catch clause
        ParamInfo tagInfoProvider = findTagInfoProvider(parameter, psiMethod, parameterList);
        if (tagInfoProvider != null) {
          buffer.append(DocumentationMarkup.CONTENT_START);
          buffer.append(generateOneParameter(tagInfoProvider));
          buffer.append(DocumentationMarkup.CONTENT_END);
        }
      }
    }

    buffer.append(DocumentationMarkup.SECTIONS_START);
    new NonCodeAnnotationGenerator(parameter, buffer).explainAnnotations(isRendered(), doHighlightSignatures());
    buffer.append(DocumentationMarkup.SECTIONS_END);
  }

  public String generateMethodParameterJavaDoc() {
    if (myElement instanceof PsiParameter) {
      PsiParameter parameter = (PsiParameter)myElement;
      PsiMethod method = PsiTreeUtil.getParentOfType(parameter, PsiMethod.class);
      if (method != null) {
        PsiParameterList parameterList = method.getParameterList();
        if (parameter.getParent() == parameterList) {
          ParamInfo tagInfoProvider = findTagInfoProvider(parameter, method, parameterList);
          if (tagInfoProvider != null) {
            PsiElement[] elements = tagInfoProvider.docTag.getDataElements();
            if (elements.length == 0) return null;
            String text = elements[0].getText();
            StringBuilder buffer = new StringBuilder();
            generateValue(buffer, tagInfoProvider, elements, text);
            return buffer.toString();
          }
        }
      }
    }
    return null;
  }

  private ParamInfo findTagInfoProvider(PsiParameter parameter, PsiMethod method, PsiParameterList parameterList) {
    PsiDocComment docComment = getDocComment(method);
    PsiDocTag[] localTags = docComment != null ? docComment.getTags() : PsiDocTag.EMPTY_ARRAY;
    int parameterIndex = parameterList.getParameterIndex(parameter);
    return findDocTag(localTags, parameter.getName(), generateOneParameterPresentableName(parameter),
                      method, parameterLocator(parameterIndex));
  }

  private void generateMethodJavaDoc(StringBuilder buffer, PsiMethod method, boolean generatePrologue) {
    if (generatePrologue) generatePrologue(buffer);

    if (!isRendered()) {
      buffer.append(DocumentationMarkup.DEFINITION_START);
      myIsSignatureGenerationInProgress = true;
      generateMethodSignature(buffer, method, SignaturePlace.Javadoc);
      myIsSignatureGenerationInProgress = false;
      buffer.append(DocumentationMarkup.DEFINITION_END);
    }

    DocTagLocator<PsiElement[]> descriptionLocator =
      (owner, comment) -> comment != null && !isEmptyDescription(comment) ? comment.getDescriptionElements() : null;

    PsiDocComment comment = getMethodDocComment(method);
    if (comment != null && !isEmptyDescription(comment)) {
      buffer.append(DocumentationMarkup.CONTENT_START);
      generateValue(buffer, comment.getDescriptionElements(), new InheritDocProvider<>() {
        @Override
        public Pair<PsiElement[], InheritDocProvider<PsiElement[]>> getInheritDoc() {
          return findInheritDocTag(method, descriptionLocator);
        }

        @Override
        public PsiClass getElement() {
          return method.getContainingClass();
        }
      });
      buffer.append(DocumentationMarkup.CONTENT_END);
      buffer.append(DocumentationMarkup.SECTIONS_START);
    }
    else {
      buffer.append(DocumentationMarkup.SECTIONS_START);

      if (!isRendered()) {
        buffer.append("<p>");
        Pair<PsiElement[], InheritDocProvider<PsiElement[]>> pair = findInheritDocTag(method, descriptionLocator);
        if (pair != null) {
          PsiElement[] elements = pair.first;
          if (elements != null) {
            PsiClass aClass = pair.second.getElement();
            startHeaderSection(buffer, JavaBundle.message(aClass.isInterface() ? "javadoc.description.copied.from.interface"
                                                                               : "javadoc.description.copied.from.class"))
              .append("<p>");
            generateLink(buffer, aClass, getStyledSpan(doSemanticHighlightingOfLinks(),
                                                       getHighlightingManager().getClassDeclarationAttributes(aClass),
                                                       JavaDocUtil.getShortestClassName(aClass, method)),
                         false);
            buffer.append(BR_TAG);
            generateValue(buffer, elements, pair.second);
            buffer.append(DocumentationMarkup.SECTION_END);
          }
        }
        else {
          PsiField field = PropertyUtil.getFieldOfGetter(method);
          if (field == null) {
            field = PropertyUtil.getFieldOfSetter(method);
          }

          if (field != null) {
            PsiDocComment fieldDocComment = field.getDocComment();
            if (fieldDocComment != null && !isEmptyDescription(fieldDocComment)) {
              startHeaderSection(buffer, JavaBundle.message("javadoc.description.copied.from.field"))
                .append("<p>");
              generateLink(buffer, field, getStyledSpan(doSemanticHighlightingOfLinks(),
                                                        getHighlightingManager().getFieldDeclarationAttributes(field), field.getName()),
                           false);
              buffer.append(BR_TAG);
              generateValue(buffer, fieldDocComment.getDescriptionElements(), ourEmptyElementsProvider);
              buffer.append(DocumentationMarkup.SECTION_END);
            }
          }
        }
      }
    }

    if (!isRendered()) {
      generateSuperMethodsSection(buffer, method, false);
      generateSuperMethodsSection(buffer, method, true);
    }

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
      if (isRendered()) {
        generateAuthorAndVersionSections(buffer, comment);
      }
    }

    if (!isRendered()) {
      new NonCodeAnnotationGenerator(method, buffer).explainAnnotations(isRendered(), doHighlightSignatures());
    }

    buffer.append(DocumentationMarkup.SECTIONS_END);
  }

  private static StringBuilder startHeaderSection(StringBuilder buffer, String message) {
    return buffer.append(DocumentationMarkup.SECTION_HEADER_START)
      .append(message)
      .append(DocumentationMarkup.SECTION_SEPARATOR);
  }

  private void generateMethodSignature(StringBuilder buffer, PsiMethod method, SignaturePlace place) {
    boolean isTooltip = place == SignaturePlace.ToolTip;
    boolean generateLink = place == SignaturePlace.Javadoc;

    generateAnnotations(buffer, method, place, true, false, true);

    if (!isTooltip) {
      generateModifiers(buffer, method, true);
    }

    String typeParamsString = generateTypeParameters(method, isTooltip);
    if (!typeParamsString.isEmpty()) {
      buffer.append(typeParamsString);
      buffer.append(NBSP);
    }

    if (method.getReturnType() != null) {
      generateType(buffer, method.getReturnType(), method, generateLink, isTooltip);
      buffer.append(NBSP);
    }
    String name = method.getName();
    appendStyledSpan(buffer, getHighlightingManager().getMethodDeclarationAttributes(method), name);

    appendStyledSpan(buffer, getHighlightingManager().getParenthesesAttributes(), "(");
    PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiFile file = method.getContainingFile();
    int indent = isTooltip ? 0 : file != null ? CodeStyle.getIndentSize(file) : 4;
    if (parameters.length > 0 && !isTooltip) {
      buffer.append(BR_TAG);
    }
    for (int i = 0; i < parameters.length; i++) {
      buffer.append(StringUtil.repeatSymbol(' ', indent));
      PsiParameter parm = parameters[i];
      generateAnnotations(buffer, parm, place, false, false, true);
      generateType(buffer, parm.getType(), method, generateLink, isTooltip);
      if (!isTooltip) {
        buffer.append(NBSP);
        appendStyledSpan(buffer, getHighlightingManager().getParameterAttributes(), parm.getName());
      }
      if (i < parameters.length - 1) {
        appendStyledSpan(buffer, getHighlightingManager().getCommaAttributes(), ",");
        buffer.append("\n");
      }
    }
    if (parameters.length > 0 && !isTooltip) {
      buffer.append(BR_TAG);
    }
    appendStyledSpan(buffer, getHighlightingManager().getParenthesesAttributes(), ")");

    PsiClassType[] refs = method.getThrowsList().getReferencedTypes();
    if (refs.length > 0) {
      buffer.append('\n');
      appendStyledSpan(buffer, getHighlightingManager().getKeywordAttributes(), "throws");
      buffer.append(NBSP);
      for (int i = 0; i < refs.length; i++) {
        generateLink(buffer, isTooltip ? refs[i].getPresentableText() : refs[i].getCanonicalText(), null, method, false);
        if (i < refs.length - 1) {
          appendStyledSpan(buffer, getHighlightingManager().getCommaAttributes(), ",");
          buffer.append(NBSP);
        }
      }
    }
  }

  private PsiDocComment getMethodDocComment(PsiMethod method) {
    PsiClass parentClass = method.getContainingClass();
    if (parentClass != null && parentClass.isEnum()) {
      PsiParameterList parameterList = method.getParameterList();
      if (method.getName().equals("values") && parameterList.isEmpty()) {
        return loadSyntheticDocComment(method, "/javadoc/EnumValues.java.template");
      }
      if (method.getName().equals("valueOf") &&
          parameterList.getParametersCount() == 1 &&
          parameterList.getParameters()[0].getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return loadSyntheticDocComment(method, "/javadoc/EnumValueOf.java.template");
      }
    }
    return getDocComment(method);
  }

  private PsiDocComment loadSyntheticDocComment(PsiMethod method, String resourceName) {
    PsiClass containingClass = method.getContainingClass();
    assert containingClass != null : method;
    String containingClassName = containingClass.getName();
    assert containingClassName != null : containingClass;

    try {
      String text;
      try (InputStream commentStream = JavaDocInfoGenerator.class.getResourceAsStream(resourceName)) {
        if (commentStream == null) return null;
        byte[] bytes = FileUtil.loadBytes(commentStream);
        text = new String(bytes, StandardCharsets.UTF_8);
      }
      text = StringUtil.replace(text, "<ClassName>", containingClassName);
      return JavaPsiFacade.getElementFactory(myProject).createDocCommentFromText(text);
    }
    catch (IOException | IncorrectOperationException e) {
      LOG.info(e);
      return null;
    }
  }

  private void generatePrologue(StringBuilder buffer) {
    URL baseUrl = getBaseUrl();
    if (baseUrl != null) {
      buffer.append("<html><head><base href=\"").append(baseUrl).append("\"></head><body>"); // used to resolve URLs of local images
    }
  }

  private URL getBaseUrl() {
    if (myElement != null) {
      PsiElement element = myElement.getNavigationElement();
      if (element != null) {
        PsiFile file = element.getContainingFile();
        if (file != null) {
          VirtualFile vFile = file.getVirtualFile();
          if (vFile != null) {
            return VfsUtilCore.convertToURL(vFile.getUrl());
          }
        }
      }
    }
    return null;
  }

  private void generateDescription(StringBuilder buffer, PsiDocComment comment) {
    PsiElement[] elements = comment.getDescriptionElements();
    generateValue(buffer, elements, ourEmptyElementsProvider);
  }

  private static boolean isEmptyDescription(PsiDocComment comment) {
    if (comment == null) return true;

    for (PsiElement description : comment.getDescriptionElements()) {
      String text = description.getText();
      if (text != null && !ourWhitespaces.matcher(text).replaceAll("").isEmpty()) {
        return false;
      }
    }

    return true;
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

    if (aClass != null) {
      String qName = aClass.getQualifiedName();
      if (qName != null) {
        String path = StringUtil.repeat("../", StringUtil.countChars(qName, '.'));
        return path.isEmpty() ? "" : path.substring(0, path.length() - 1);
      }
    }

    return "";
  }

  private void generateValue(StringBuilder buffer, ParamInfo tag, PsiElement[] elements, String text) {
    int spaceIndex = text.indexOf(' ');
    if (spaceIndex >= 0) {
      buffer.append(text.substring(spaceIndex));
    }
    generateValue(buffer, elements, 1, mapProvider(tag.inheritDocTagProvider, true));
  }

  private void generateValue(StringBuilder buffer,
                             PsiElement[] elements,
                             int startIndex,
                             InheritDocProvider<PsiElement[]> provider) {
    int predictOffset = startIndex < elements.length ? elements[startIndex].getTextOffset() + elements[startIndex].getText().length() : 0;
    for (int i = startIndex; i < elements.length; i++) {
      if (elements[i].getTextOffset() > predictOffset) buffer.append(' ');
      predictOffset = elements[i].getTextOffset() + elements[i].getText().length();
      PsiElement element = elements[i];
      if (element instanceof PsiInlineDocTag) {
        PsiInlineDocTag tag = (PsiInlineDocTag)element;
        String tagName = tag.getName();
        if (tagName.equals(LINK_TAG)) {
          generateLinkValue(tag, buffer, false);
        }
        else if (tagName.equals(LITERAL_TAG)) {
          generateLiteralValue(buffer, tag, true);
        }
        else if (tagName.equals(CODE_TAG) || tagName.equals(SYSTEM_PROPERTY_TAG)) {
          generateCodeValue(tag, buffer);
        }
        else if (tagName.equals(LINKPLAIN_TAG)) {
          generateLinkValue(tag, buffer, true);
        }
        else if (tagName.equals(INHERIT_DOC_TAG)) {
          Pair<PsiElement[], InheritDocProvider<PsiElement[]>> inheritInfo = provider.getInheritDoc();
          if (inheritInfo != null) {
            generateValue(buffer, inheritInfo.first, inheritInfo.second);
          }
        }
        else if (tagName.equals(DOC_ROOT_TAG)) {
          buffer.append(getDocRoot());
        }
        else if (tagName.equals(VALUE_TAG)) {
          generateValueValue(tag, buffer, element);
        }
      }
      else {
        appendPlainText(buffer, element.getText());
      }
    }
  }

  private static boolean isCodeBlock(PsiInlineDocTag tag) {
    PsiElement prevSibling = tag.getPrevSibling();
    return CODE_TAG.equals(tag.getName())
           && (prevSibling instanceof PsiDocToken)
           && StringUtil.endsWithIgnoreCase(StringUtil.trim(prevSibling.getText()), "<pre>");
  }

  private void generateCodeValue(PsiInlineDocTag tag, StringBuilder buffer) {
    boolean isCodeBlock = isCodeBlock(tag);

    if (isCodeBlock) {
      // remove excess whitespaces between tags e.g. in `<pre>  {@code`
      int lastNonWhite = buffer.length() - 1;
      while (buffer.charAt(lastNonWhite) == ' ') lastNonWhite--;
      buffer.setLength(lastNonWhite + 1);
    }

    buffer.append("<code style='font-size:");
    buffer.append(DocumentationSettings.getMonospaceFontSizeCorrection(isRendered()));
    buffer.append("%;'>");
    int pos = buffer.length();

    StringBuilder codeSnippetBuilder = new StringBuilder();
    generateLiteralValue(codeSnippetBuilder, tag, false);
    String codeSnippet = codeSnippetBuilder.toString();
    if (isCodeBlock) {
      codeSnippet = StringsKt.trimIndent(codeSnippet);
    }

    if (isCodeBlock && doHighlightCodeBlocks()
        || !isCodeBlock && getInlineCodeHighlightingMode() == InlineCodeHighlightingMode.SEMANTIC_HIGHLIGHTING) {
      // highlights code by lexer
      codeSnippetBuilder.setLength(0);
      appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(true, codeSnippetBuilder, tag.getProject(), tag.getLanguage(), codeSnippet);
      codeSnippet = codeSnippetBuilder.toString();
    }

    if (isCodeBlock && doHighlightCodeBlocks()
        || !isCodeBlock && getInlineCodeHighlightingMode() != InlineCodeHighlightingMode.NO_HIGHLIGHTING) {
      // highlights plain code as HighlighterColors.TEXT
      codeSnippetBuilder.setLength(0);
      TextAttributes codeAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(HighlighterColors.TEXT).clone();
      codeAttributes.setBackgroundColor(null);
      appendStyledSpan(true, codeSnippetBuilder, codeAttributes, codeSnippet);
      codeSnippet = codeSnippetBuilder.toString();
    }

    if (isCodeBlock) {
      // indent code block
      codeSnippet = Arrays.stream(codeSnippet.contains(BR_TAG) ? codeSnippet.split(BR_TAG) : codeSnippet.split("\n"))
        .map(it -> "  " + it)
        .collect(Collectors.joining(BR_TAG));
    }
    else {
      // we are in inline @code block, we should remove all new lines
      codeSnippet = codeSnippet.replace(BR_TAG, "");
    }

    buffer.append(codeSnippet);
    buffer.append("</code>");
    if (buffer.charAt(pos) == '\n') buffer.insert(pos, ' '); // line break immediately after opening tag is ignored by JEditorPane
  }

  private void generateLiteralValue(StringBuilder buffer, PsiDocTag tag, boolean doEscaping) {
    StringBuilder tmpBuffer = new StringBuilder();
    PsiElement[] children = tag.getChildren();
    for (int i = 2; i < children.length - 1; i++) { // process all children except tag opening/closing elements
      PsiElement child = children[i];
      if (child instanceof PsiDocToken && ((PsiDocToken)child).getTokenType() == JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) continue;
      String elementText = child.getText();
      if (child instanceof PsiWhiteSpace) {
        int pos = elementText.lastIndexOf('\n');
        if (pos >= 0) elementText = elementText.substring(0, pos + 1); // skip whitespace before leading asterisk
      }
      appendPlainText(tmpBuffer, doEscaping ? StringUtil.escapeXmlEntities(elementText) : elementText);
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
        String text = StringUtil.toLowerCase(sibling.getText());
        int pos = text.lastIndexOf("pre>");
        if (pos > 0) {
          switch (text.charAt(pos - 1)) {
            case '<':
              return true;
            case '/':
              return false;
          }
        }
      }
      sibling = sibling.getPrevSibling();
    }
    return false;
  }

  private static void appendPlainText(StringBuilder buffer, String text) {
    buffer.append(StringUtil.replaceUnicodeEscapeSequences(text));
  }

  private void generateLinkValue(PsiInlineDocTag tag, StringBuilder buffer, boolean plainLink) {
    PsiElement[] tagElements = tag.getDataElements();
    String text = createLinkText(tagElements);
    if (!text.isEmpty()) {
      generateLink(buffer, text, tagElements[0], plainLink);
    }
  }

  private void generateValueValue(PsiInlineDocTag tag, StringBuilder buffer, PsiElement element) {
    String text = createLinkText(tag.getDataElements());
    PsiField valueField = null;
    if (text.isEmpty()) {
      if (myElement instanceof PsiField) valueField = (PsiField)myElement;
    }
    else {
      if (text.indexOf('#') == -1) {
        text = '#' + text;
      }
      PsiElement target = null;
      try {
        target = JavaDocUtil.findReferenceTarget(PsiManager.getInstance(myProject), text, myElement);
      }
      catch (IndexNotReadyException e) {
        LOG.debug(e);
      }
      if (target instanceof PsiField) {
        valueField = (PsiField)target;
      }
    }

    Object value = null;
    if (valueField != null) {
      PsiExpression initializer = valueField.getInitializer();
      value = DumbService.isDumb(myProject) ? null : JavaConstantExpressionEvaluator.computeConstantExpression(initializer, false);
    }

    if (value != null) {
      String valueText = StringUtil.escapeXmlEntities(value.toString());
      if (value instanceof String) valueText = '"' + valueText + '"';
      if (valueField.equals(myElement)) {
        buffer.append(valueText); // don't generate link to itself
      }
      else {
        generateLink(buffer, valueField, valueText, true);
      }
    }
    else {
      buffer.append(element.getText());
    }
  }

  protected String createLinkText(PsiElement[] tagElements) {
    int predictOffset = tagElements.length > 0 ? tagElements[0].getTextOffset() + tagElements[0].getText().length() : 0;
    StringBuilder buffer = new StringBuilder();
    for (PsiElement tagElement : tagElements) {
      if (tagElement.getTextOffset() > predictOffset) buffer.append(' ');
      predictOffset = tagElement.getTextOffset() + tagElement.getText().length();

      collectElementText(buffer, tagElement);
    }
    return buffer.toString().trim();
  }

  protected void collectElementText(StringBuilder buffer, PsiElement element) {
    element.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        super.visitElement(element);
        if (element instanceof PsiWhiteSpace ||
            element instanceof PsiJavaToken ||
            element instanceof PsiDocToken && ((PsiDocToken)element).getTokenType() != JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
          buffer.append(element.getText());
        }
      }
    });
  }

  private void generateDeprecatedSection(StringBuilder buffer, PsiDocComment comment) {
    generateSingleTagSection(buffer, comment, "deprecated", JavaBundle.messagePointer("javadoc.deprecated"));
  }

  private void generateSingleTagSection(StringBuilder buffer,
                                        PsiDocComment comment,
                                        String tagName,
                                        Supplier<String> computePresentableName) {
    PsiDocTag tag = comment.findTagByName(tagName);
    if (tag != null) {
      startHeaderSection(buffer, computePresentableName.get()).append("<p>");
      generateValue(buffer, tag.getDataElements(), ourEmptyElementsProvider);
      buffer.append(DocumentationMarkup.SECTION_END);
    }
  }

  private void generateSinceSection(StringBuilder buffer, PsiDocComment comment) {
    generateSingleTagSection(buffer, comment, "since", JavaBundle.messagePointer("javadoc.since"));
  }

  protected void generateSeeAlsoSection(StringBuilder buffer, PsiDocComment comment) {
    PsiDocTag[] tags = comment.findTagsByName("see");
    if (tags.length > 0) {
      startHeaderSection(buffer, JavaBundle.message("javadoc.see.also")).append("<p>");
      for (int i = 0; i < tags.length; i++) {
        PsiDocTag tag = tags[i];
        PsiElement[] elements = tag.getDataElements();
        if (elements.length > 0) {
          String text = createLinkText(elements);
          if (StringUtil.startsWithChar(text, '<')) {
            buffer.append(text);
          }
          else if (StringUtil.startsWithChar(text, '"')) {
            appendPlainText(buffer, text);
          }
          else {
            generateLink(buffer, text, comment, false);
          }
        }
        if (i < tags.length - 1) {
          buffer.append(",");
          buffer.append(BR_TAG);
        }
      }
      buffer.append(DocumentationMarkup.SECTION_END);
    }
  }

  protected void generateAuthorSection(StringBuilder buffer, PsiDocComment comment) {
    PsiDocTag[] tags = comment.findTagsByName("author");
    if (tags.length > 0) {
      startHeaderSection(buffer, JavaBundle.message("javadoc.author")).append("<p>");
      for (int i = 0; i < tags.length; i++) {
        StringBuilder tmp = new StringBuilder();
        generateValue(tmp, tags[i].getDataElements(), ourEmptyElementsProvider);
        buffer.append(tmp.toString().trim());
        if (i < tags.length - 1) {
          buffer.append(", ");
        }
      }
      buffer.append(DocumentationMarkup.SECTION_END);
    }
  }

  private void generateParametersSection(StringBuilder buffer, PsiMethod method, PsiDocComment comment) {
    PsiParameter[] params = method.getParameterList().getParameters();
    PsiDocTag[] localTags = comment != null ? comment.findTagsByName("param") : PsiDocTag.EMPTY_ARRAY;
    List<ParamInfo> collectedTags = new ArrayList<>();
    for (int i = 0; i < params.length; i++) {
      PsiParameter param = params[i];
      String presentableName = generateOneParameterPresentableName(param);
      DocTagLocator<PsiDocTag> tagLocator = parameterLocator(i);
      ParamInfo parmTag = findDocTag(localTags, param.getName(), presentableName, method, tagLocator);
      if (parmTag != null) {
        collectedTags.add(parmTag);
      }
    }
    generateParametersSection(buffer, CodeInsightBundle.message("javadoc.parameters"), collectedTags);
  }

  private void generateTypeParametersSection(StringBuilder buffer, PsiMethod method, PsiDocComment comment) {
    PsiDocTag[] localTags = comment == null ? PsiDocTag.EMPTY_ARRAY : comment.findTagsByName("param");
    PsiTypeParameter[] typeParameters = method.getTypeParameters();
    List<ParamInfo> collectedTags = new ArrayList<>();
    for (int i = 0; i < typeParameters.length; i++) {
      PsiTypeParameter typeParameter = typeParameters[i];
      String presentableName = generateOneTypeParameterPresentableName(typeParameter);
      DocTagLocator<PsiDocTag> tagLocator = typeParameterLocator(i);
      ParamInfo parmTag = findDocTag(localTags, typeParameter.getName(), presentableName, method, tagLocator);
      if (parmTag != null) {
        collectedTags.add(parmTag);
      }
    }
    generateParametersSection(buffer, JavaBundle.message("javadoc.type.parameters"), collectedTags);
  }

  private void generateParametersSection(StringBuilder buffer, String titleMessage, List<ParamInfo> collectedTags) {
    if (!collectedTags.isEmpty()) {
      startHeaderSection(buffer, titleMessage)
        .append(StringUtil.join(collectedTags, tag -> generateOneParameter(tag), "<p>"))
        .append(DocumentationMarkup.SECTION_END);
    }
  }

  @Nullable
  private ParamInfo findDocTag(PsiDocTag[] localTags,
                               String paramName,
                               String presentableName,
                               PsiMethod method,
                               DocTagLocator<PsiDocTag> tagLocator) {
    PsiDocTag localTag = getTagByName(localTags, paramName);
    if (localTag != null) {
      return new ParamInfo(paramName, presentableName, localTag, new InheritDocProvider<>() {
        @Override
        public Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> getInheritDoc() {
          return isRendered() ? null : findInheritDocTag(method, tagLocator);
        }

        @Override
        public PsiClass getElement() {
          return method.getContainingClass();
        }
      });
    }
    if (isRendered()) return null;
    Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> tag = findInheritDocTag(method, tagLocator);
    return tag == null ? null : new ParamInfo(paramName, presentableName, tag);
  }

  private String generateOneParameter(ParamInfo tag) {
    PsiElement[] elements = tag.docTag.getDataElements();
    if (elements.length == 0) return "";
    String text = elements[0].getText();
    StringBuilder buffer = new StringBuilder();
    buffer.append(tag.presentableName);
    StringBuilder descriptionBuffer = new StringBuilder();
    generateValue(descriptionBuffer, tag, elements, text);
    if (!StringUtil.isEmptyOrSpaces(descriptionBuffer)) {
      buffer.append(" &ndash; ");
      buffer.append(descriptionBuffer);
    }
    return buffer.toString();
  }

  private void generateReturnsSection(StringBuilder buffer, PsiMethod method, PsiDocComment comment) {
    PsiDocTag tag = comment == null ? null : new ReturnTagLocator().find(method, comment);
    Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> pair = tag == null ? null : new Pair<>(tag, new InheritDocProvider<>() {
      @Override
      public Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> getInheritDoc() {
        return isRendered() ? null : findInheritDocTag(method, new ReturnTagLocator());
      }

      @Override
      public PsiClass getElement() {
        return method.getContainingClass();
      }
    });

    if (!isRendered() && pair == null && myElement instanceof PsiMethod) {
      pair = findInheritDocTag((PsiMethod)myElement, new ReturnTagLocator());
    }

    if (pair != null) {
      startHeaderSection(buffer, CodeInsightBundle.message("javadoc.returns"))
        .append("<p>");
      generateValue(buffer, pair.first.getDataElements(), mapProvider(pair.second, false));
      buffer.append(DocumentationMarkup.SECTION_END);
    }
  }

  private static PsiDocTag[] getThrowsTags(PsiDocComment comment) {
    if (comment == null) return PsiDocTag.EMPTY_ARRAY;
    PsiDocTag[] tags1 = comment.findTagsByName("throws");
    PsiDocTag[] tags2 = comment.findTagsByName("exception");
    return ArrayUtil.mergeArrays(tags1, tags2);
  }

  private static boolean areWeakEqual(String one, String two) {
    return one.equals(two) || one.endsWith('.' + two) || two.endsWith('.' + one);
  }

  private void generateThrowsSection(StringBuilder buffer, PsiMethod method, PsiDocComment comment) {
    PsiDocTag[] localTags = getThrowsTags(comment);
    PsiDocTag[] thrownTags = localTags;
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(method.getProject());

    Set<PsiClass> reported = new HashSet<>();
    if (!isRendered()) {
      for (HierarchicalMethodSignature signature : method.getHierarchicalMethodSignature().getSuperSignatures()) {
        PsiMethod superMethod = ObjectUtils.tryCast(signature.getMethod().getNavigationElement(), PsiMethod.class);
        PsiDocComment docComment = superMethod != null ? superMethod.getDocComment() : null;
        if (docComment != null) {
          PsiDocTag[] uncheckedExceptions = Arrays.stream(getThrowsTags(docComment)).filter(tag -> {
            PsiDocTagValue valueElement = tag.getValueElement();
            if (valueElement == null) return false;
            if (Arrays.stream(localTags)
              .map(PsiDocTag::getValueElement)
              .filter(Objects::nonNull)
              .anyMatch(docTagValue -> areWeakEqual(docTagValue.getText(), valueElement.getText()))) {
              return false;
            }
            PsiClass exClass = psiFacade.getResolveHelper().resolveReferencedClass(valueElement.getText(), docComment);
            if (exClass == null) return false;
            return ExceptionUtil.isUncheckedException(exClass) && reported.add(exClass);
          }).toArray(PsiDocTag[]::new);
          thrownTags = ArrayUtil.mergeArrays(thrownTags, uncheckedExceptions);
        }
      }
    }

    LinkedList<Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>> collectedTags = new LinkedList<>();
    List<PsiClassType> declaredThrows = isRendered() ? Collections.emptyList()
                                                     : new ArrayList<>(Arrays.asList(method.getThrowsList().getReferencedTypes()));

    for (int i = thrownTags.length - 1; i > -1; i--) {
      PsiDocTagValue valueElement = thrownTags[i].getValueElement();

      if (valueElement != null) {
        for (Iterator<PsiClassType> iterator = declaredThrows.iterator(); iterator.hasNext(); ) {
          PsiClassType classType = iterator.next();
          if (Comparing.strEqual(valueElement.getText(), classType.getClassName()) ||
              Comparing.strEqual(valueElement.getText(), classType.getCanonicalText())) {
            iterator.remove();
            break;
          }
        }

        Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> tag = isRendered() ? null
                                                                          : findInheritDocTag(method,
                                                                                              exceptionLocator(valueElement.getText()));
        collectedTags.addFirst(new Pair<>(thrownTags[i], new InheritDocProvider<>() {
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

    for (PsiClassType trouser : declaredThrows) {
      if (trouser != null) {
        String paramName = trouser.getCanonicalText();
        Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> parmTag = null;

        for (PsiDocTag localTag : thrownTags) {
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
            PsiDocTag tag = psiFacade.getElementFactory().createDocTagFromText("@exception " + paramName);
            collectedTags.addLast(Pair.create(tag, ourEmptyProvider));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    }

    if (!collectedTags.isEmpty()) {
      startHeaderSection(buffer, CodeInsightBundle.message("javadoc.throws"));
      for (Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> tag : collectedTags) {
        PsiElement[] elements = tag.first.getDataElements();
        if (elements.length == 0) continue;
        buffer.append("<p>");
        String text = elements[0].getText();
        int index = JavaDocUtil.extractReference(text);
        String refText = text.substring(0, index).trim();
        generateLink(buffer, refText, null, method, false);
        String rest = text.substring(index);
        if (!rest.isEmpty() || elements.length > 1) buffer.append(" &ndash; ");
        buffer.append(rest);
        generateValue(buffer, elements, 1, mapProvider(tag.second, true));
      }
      buffer.append(DocumentationMarkup.SECTION_END);
    }
  }

  private static @Nullable String generateLink(@NotNull PsiElement element, String label, boolean plainLink, boolean isRenderedDoc) {
    String refText = JavaDocUtil.getReferenceText(element.getProject(), element);
    if (refText != null) {
      StringBuilder linkBuilder = new StringBuilder();
      DocumentationManagerUtil.createHyperlink(linkBuilder, element, refText, label, plainLink, isRenderedDoc);
      return linkBuilder.toString();
    }
    return null;
  }

  private void generateSuperMethodsSection(StringBuilder buffer, PsiMethod method, boolean overrides) {
    PsiClass parentClass = method.getContainingClass();
    if (parentClass == null) return;
    if (parentClass.isInterface() && !overrides) return;
    PsiMethod[] supers = method.findSuperMethods();
    Arrays.sort(supers, Comparator.comparing(m -> {
      PsiClass aClass = m.getContainingClass();
      return aClass == null ? null : aClass.getName();
    }));
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
        startHeaderSection(buffer, JavaBundle.message(overrides ? "javadoc.method.overrides" : "javadoc.method.specified.by"));
        buffer.append("<p>");
        headerGenerated = true;
      }
      else {
        buffer.append(BR_TAG);
      }

      StringBuilder methodBuffer = new StringBuilder();
      generateLink(
        methodBuffer, superMethod,
        getStyledSpan(doSemanticHighlightingOfLinks(),
                      getHighlightingManager().getMethodDeclarationAttributes(superMethod), superMethod.getName()), false);
      StringBuilder classBuffer = new StringBuilder();
      generateLink(classBuffer, superClass,
                   getStyledSpan(doSemanticHighlightingOfLinks(),
                                 getHighlightingManager().getClassDeclarationAttributes(superClass), superClass.getName()), false);
      if (superClass.isInterface()) {
        buffer.append(JavaBundle.message("javadoc.method.in.interface", methodBuffer.toString(), classBuffer.toString()));
      }
      else {
        buffer.append(JavaBundle.message("javadoc.method.in.class", methodBuffer.toString(), classBuffer.toString()));
      }
    }
    if (headerGenerated) {
      buffer.append(DocumentationMarkup.SECTION_END);
    }
  }

  private void generateLink(StringBuilder buffer, String linkText, @NotNull PsiElement context, boolean plainLink) {
    int index = JavaDocUtil.extractReference(linkText);
    String refText = linkText.substring(0, index).trim();
    String label = StringUtil.nullize(linkText.substring(index).trim());
    generateLink(buffer, refText, label, context, plainLink);
  }

  void generateLink(StringBuilder buffer, PsiElement element, String label, boolean plainLink) {
    String refText = JavaDocUtil.getReferenceText(element.getProject(), element);
    if (refText != null) {
      DocumentationManagerUtil.createHyperlink(buffer, element, refText, label, plainLink, isRendered());
    }
  }

  /**
   * @return Length of the generated label.
   */
  int generateLink(StringBuilder buffer, String refText, String label, @NotNull PsiElement context, boolean plainLink) {
    if (label == null) {
      PsiManager manager = context.getManager();
      label = JavaDocUtil.getLabelText(manager.getProject(), manager, refText, context);
    }
    LOG.assertTrue(refText != null, "refText appears to be null.");
    PsiElement target = null;
    try {
      target = JavaDocUtil.findReferenceTarget(context.getManager(), refText, context, false);
    }
    catch (IndexNotReadyException e) {
      LOG.debug(e);
    }
    appendMaybeUnresolvedLink(buffer, target, label, context.getProject(), plainLink);
    return StringUtil.stripHtml(label, true).length();
  }

  public void appendMaybeUnresolvedLink(
    StringBuilder buffer,
    @Nullable PsiElement target,
    String label,
    @NotNull Project project,
    boolean plainLink
  ) {
    if (target == null && DumbService.isDumb(project)) {
      buffer.append(label);
    }
    else if (target == null) {
      buffer.append("<font color=red>").append(label).append("</font>");
    }
    else {
      String highlightedLabel = myIsSignatureGenerationInProgress && doHighlightSignatures() || doSemanticHighlightingOfLinks()
                                ? tryHighlightLinkLabel(target, label)
                                : label;
      generateLink(buffer, target, highlightedLabel, plainLink);
    }
  }

  /**
   * If highlighted links has the same color as highlighted inline code blocks they will be indistinguishable.
   * In this case we should change link color to standard hyperlink color which we believe is apriori different.
   */
  private @NotNull TextAttributes tuneAttributesForLink(@NotNull TextAttributes attributes) {
    EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributes defaultText = globalScheme.getAttributes(HighlighterColors.TEXT);
    TextAttributes identifier = globalScheme.getAttributes(DefaultLanguageHighlighterColors.IDENTIFIER);

    if (!myIsSignatureGenerationInProgress &&
        (Objects.equals(attributes.getForegroundColor(), defaultText.getForegroundColor())
         || Objects.equals(attributes.getForegroundColor(), identifier.getForegroundColor()))) {
      TextAttributes tuned = attributes.clone();
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        tuned.setForegroundColor(globalScheme.getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES).getForegroundColor());
      }
      else {
        tuned.setForegroundColor(globalScheme.getColor(DefaultLanguageHighlighterColors.DOC_COMMENT_LINK));
      }
      return tuned;
    }
    return attributes;
  }

  private @NotNull String tryHighlightLinkLabel(@NotNull PsiElement element, @NotNull String label) {
    if (element instanceof PsiClass) {
      return getStyledSpan(true, tuneAttributesForLink(getHighlightingManager().getClassDeclarationAttributes((PsiClass)element)), label);
    }
    if (element instanceof PsiPackage) {
      return getStyledSpan(true, tuneAttributesForLink(getHighlightingManager().getClassNameAttributes()), label);
    }
    else if (element instanceof PsiMethod) {
      return tryHighlightLinkOnClassMember(
        (PsiMember)element, tuneAttributesForLink(getHighlightingManager().getMethodDeclarationAttributes((PsiMethod)element)), label);
    }
    else if (element instanceof PsiField) {
      return tryHighlightLinkOnClassMember(
        (PsiField)element, tuneAttributesForLink(getHighlightingManager().getFieldDeclarationAttributes((PsiField)element)), label);
    }
    else {
      return getHighlightedByLexerAndEncodedAsHtmlCodeSnippet(element.getProject(), element.getLanguage(), label);
    }
  }

  private @NotNull String tryHighlightLinkOnClassMember(
    @NotNull PsiMember member,
    @NotNull TextAttributes labelAttributes,
    @NotNull String label
  ) {
    StringBuilder buffer = new StringBuilder();
    int openParenIndex = label.indexOf("(");
    if (openParenIndex == -1) openParenIndex = label.length();
    int classNameIndex = label.substring(0, openParenIndex).lastIndexOf(".");
    if (classNameIndex != -1) {
      PsiClass containingClass = member.getContainingClass();
      TextAttributes containingClassAttributes =
        containingClass != null ? getHighlightingManager().getClassDeclarationAttributes(containingClass)
                                : getHighlightingManager().getClassNameAttributes();
      containingClassAttributes = tuneAttributesForLink(containingClassAttributes);
      appendStyledSpan(true, buffer, containingClassAttributes, label.substring(0, classNameIndex));
      appendStyledSpan(true, buffer, getHighlightingManager().getDotAttributes(), ".");
    }
    classNameIndex++;
    appendStyledSpan(true, buffer, labelAttributes, label.substring(classNameIndex, openParenIndex));
    if (openParenIndex == label.length()) return buffer.toString();
    appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
      true, buffer, member.getProject(), member.getLanguage(), label.substring(openParenIndex));
    return buffer.toString();
  }

  /**
   * @return Length of the generated label.
   */
  public int generateType(StringBuilder buffer, PsiType type, PsiElement context) {
    return generateType(buffer, type, context, true);
  }

  /**
   * @return Length of the generated label.
   */
  public int generateType(StringBuilder buffer, PsiType type, PsiElement context, boolean generateLink) {
    return generateType(buffer, type, context, generateLink, false);
  }

  /**
   * @return Length of the generated label.
   */
  public int generateType(StringBuilder buffer, PsiType type, PsiElement context, boolean generateLink, boolean useShortNames) {
    if (type instanceof PsiArrayType) {
      int rest = generateType(buffer, ((PsiArrayType)type).getComponentType(), context, generateLink, useShortNames);

      int len = generateTypeAnnotations(buffer, type, context, generateLink, true);
      if (type instanceof PsiEllipsisType) {
        buffer.append("...");
        return len + rest + 3;
      }
      else {
        appendStyledSpan(buffer, getHighlightingManager().getBracketsAttributes(), "[]");
        return len + rest + 2;
      }
    }

    int typAnnoLength = generateTypeAnnotations(buffer, type, context, generateLink, false);

    if (type instanceof PsiPrimitiveType) {
      String text = type.getCanonicalText();
      appendStyledSpan(buffer, getHighlightingManager().getKeywordAttributes(), StringUtil.escapeXmlEntities(text));
      return typAnnoLength + text.length();
    }

    if (type instanceof PsiCapturedWildcardType) {
      type = ((PsiCapturedWildcardType)type).getWildcard();
    }

    if (type instanceof PsiWildcardType) {
      PsiWildcardType wt = (PsiWildcardType)type;
      appendStyledSpan(buffer, getHighlightingManager().getOperationSignAttributes(), "?");
      PsiType bound = wt.getBound();
      if (bound != null) {
        String keyword = wt.isExtends() ? " extends " : " super ";
        appendStyledSpan(buffer, getHighlightingManager().getKeywordAttributes(), keyword);
        return typAnnoLength + generateType(buffer, bound, context, generateLink, useShortNames) + 1 + keyword.length();
      }
      else {
        return typAnnoLength + 1;
      }
    }

    if (type instanceof PsiClassType) {
      PsiClass psiClass = null;
      PsiSubstitutor psiSubst = null;
      try {
        PsiClassType.ClassResolveResult result = ((PsiClassType)type).resolveGenerics();
        psiClass = result.getElement();
        psiSubst = result.getSubstitutor();
      }
      catch (IndexNotReadyException e) {
        LOG.debug(e);
      }

      if (psiClass == null) {
        if (DumbService.isDumb(context.getProject())) {
          String text = ((PsiClassType)type).getClassName();
          buffer.append(StringUtil.escapeXmlEntities(text));
          return typAnnoLength + text.length();
        }
        String canonicalText = type.getCanonicalText();
        String text = "<font color=red>" + StringUtil.escapeXmlEntities(canonicalText) + "</font>";
        buffer.append(text);
        return typAnnoLength + canonicalText.length();
      }

      String qName = psiClass.getQualifiedName();

      if (qName == null || psiClass instanceof PsiTypeParameter) {
        String typeText = useShortNames ? type.getPresentableText() : type.getCanonicalText();
        appendStyledSpan(
          buffer, getHighlightingManager().getTypeParameterNameAttributes(), StringUtil.escapeXmlEntities(typeText));
        return typAnnoLength + typeText.length();
      }

      String name = useShortNames ? getClassNameWithOuterClasses(psiClass) : qName;

      int length = typAnnoLength;
      if (generateLink) {
        length += generateLink(buffer, name, null, context, false);
      }
      else {
        appendStyledSpan(buffer, getHighlightingManager().getClassDeclarationAttributes(psiClass), name);
        length += name.length();
      }

      if (psiClass.hasTypeParameters()) {
        StringBuilder subst = new StringBuilder();

        PsiTypeParameter[] params = psiClass.getTypeParameters();

        appendStyledSpan(subst, getHighlightingManager().getOperationSignAttributes(), LT);
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
            appendStyledSpan(subst, getHighlightingManager().getCommaAttributes(), ", ");
            length += 2;
          }
        }

        appendStyledSpan(subst, getHighlightingManager().getOperationSignAttributes(), GT);
        length++;
        if (goodSubst) {
          buffer.append(subst);
        }
      }

      return length;
    }

    if (type instanceof PsiDisjunctionType || type instanceof PsiIntersectionType) {
      if (!generateLink) {
        String canonicalText = useShortNames ? type.getPresentableText() : type.getCanonicalText();
        buffer.append(StringUtil.escapeXmlEntities(canonicalText));
        return typAnnoLength + canonicalText.length();
      }
      else {
        String separator = type instanceof PsiDisjunctionType ? " | " : " & ";
        List<PsiType> componentTypes;
        if (type instanceof PsiIntersectionType) {
          componentTypes = Arrays.asList(((PsiIntersectionType)type).getConjuncts());
        }
        else {
          componentTypes = ((PsiDisjunctionType)type).getDisjunctions();
        }
        int length = typAnnoLength;
        for (PsiType psiType : componentTypes) {
          if (length > 0) {
            appendStyledSpan(buffer, getHighlightingManager().getOperationSignAttributes(), separator);
            length += 3;
          }
          length += generateType(buffer, psiType, context, true, useShortNames);
        }
        return length;
      }
    }

    return 0;
  }

  private static String getClassNameWithOuterClasses(@NotNull PsiClass cls) {
    StringBuilder result = new StringBuilder();
    for (; cls != null; cls = cls.getContainingClass()) {
      String name = cls.getName();
      if (name == null) break;
      if (result.length() > 0) result.insert(0, '.');
      result.insert(0, name);
    }
    return result.toString();
  }

  String generateTypeParameters(PsiTypeParameterListOwner owner, boolean useShortNames) {
    if (owner.hasTypeParameters()) {
      PsiTypeParameter[] parameters = owner.getTypeParameters();

      StringBuilder buffer = new StringBuilder();
      appendStyledSpan(buffer, getHighlightingManager().getOperationSignAttributes(), LT);

      for (int i = 0; i < parameters.length; i++) {
        PsiTypeParameter p = parameters[i];

        generateTypeAnnotations(buffer, p, p, true, false);

        appendStyledSpan(
          buffer,
          getHighlightingManager().getTypeParameterNameAttributes(),
          Objects.requireNonNullElse(p.getName(), CommonBundle.getErrorTitle()));

        PsiClassType[] refs = JavaDocUtil.getExtendsList(p);
        if (refs.length > 0) {
          appendStyledSpan(buffer, getHighlightingManager().getKeywordAttributes(), " extends ");
          for (int j = 0; j < refs.length; j++) {
            generateType(buffer, refs[j], owner, true, useShortNames);
            if (j < refs.length - 1) {
              appendStyledSpan(buffer, getHighlightingManager().getOperationSignAttributes(), " & ");
            }
          }
        }

        if (i < parameters.length - 1) {
          appendStyledSpan(buffer, getHighlightingManager().getCommaAttributes(), ", ");
        }
      }

      appendStyledSpan(buffer, getHighlightingManager().getOperationSignAttributes(), GT);
      return buffer.toString();
    }

    return "";
  }

  private <T> Pair<T, InheritDocProvider<T>> searchDocTagInOverriddenMethod(PsiMethod method, PsiClass aSuper, DocTagLocator<T> loc) {
    if (aSuper != null) {
      PsiMethod overridden = findMethodInSuperClass(method, aSuper);
      if (overridden != null) {
        T tag = loc.find(overridden, getDocComment(overridden));
        if (tag != null) {
          return new Pair<>(tag, new InheritDocProvider<>() {
            @Override
            public Pair<T, InheritDocProvider<T>> getInheritDoc() {
              return findInheritDocTag(overridden, loc);
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
      PsiMethod overridden = aSuper.findMethodBySignature(superMethod, false);
      if (overridden != null) return overridden;
    }
    return null;
  }

  @Nullable
  private <T> Pair<T, InheritDocProvider<T>> searchDocTagInSupers(PsiClassType[] supers,
                                                                  PsiMethod method,
                                                                  DocTagLocator<T> loc,
                                                                  Set<PsiClass> visitedClasses) {
    try {
      for (PsiClassType superType : supers) {
        PsiClass aSuper = superType.resolve();
        if (aSuper != null) {
          Pair<T, InheritDocProvider<T>> tag = searchDocTagInOverriddenMethod(method, aSuper, loc);
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
    }
    catch (IndexNotReadyException e) {
      LOG.debug(e);
    }
    return null;
  }

  private <T> Pair<T, InheritDocProvider<T>> findInheritDocTagInClass(PsiMethod aMethod,
                                                                      PsiClass aClass,
                                                                      DocTagLocator<T> loc,
                                                                      Set<PsiClass> visitedClasses) {
    if (aClass == null) return null;

    Pair<T, InheritDocProvider<T>> delegate = findInheritDocTagInDelegate(aMethod, loc);
    if (delegate != null) return delegate;

    if (aClass instanceof PsiAnonymousClass) {
      return searchDocTagInSupers(new PsiClassType[]{((PsiAnonymousClass)aClass).getBaseClassType()}, aMethod, loc, visitedClasses);
    }

    PsiClassType[] implementsTypes = aClass.getImplementsListTypes();
    Pair<T, InheritDocProvider<T>> tag = searchDocTagInSupers(implementsTypes, aMethod, loc, visitedClasses);
    if (tag != null) return tag;

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

    return Pair.create(tag, new InheritDocProvider<>() {
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
    return aClass != null ? findInheritDocTagInClass(method, aClass, loc, new HashSet<>()) : null;
  }

  private static final class ParamInfo {
    private final String name;
    private final String presentableName;
    private final PsiDocTag docTag;
    private final InheritDocProvider<PsiDocTag> inheritDocTagProvider;

    private ParamInfo(String paramName, String presentableName, PsiDocTag tag, InheritDocProvider<PsiDocTag> provider) {
      name = paramName;
      this.presentableName = presentableName;
      docTag = tag;
      inheritDocTagProvider = provider;
    }

    private ParamInfo(
      String paramName,
      String presentableName,
      @NotNull Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> tagWithInheritProvider
    ) {
      this(paramName, presentableName, tagWithInheritProvider.first, tagWithInheritProvider.second);
    }
  }

  private static class ReturnTagLocator implements DocTagLocator<PsiDocTag> {
    @Override
    public PsiDocTag find(PsiDocCommentOwner owner, PsiDocComment comment) {
      if (comment != null) {
        PsiDocTag returnTag = comment.findTagByName("return");
        if (returnTag != null) {
          return returnTag;
        }
        if (PsiUtil.isLanguageLevel16OrHigher(comment)) {
          for (PsiElement child : comment.getChildren()) {
            if (child instanceof PsiDocTag && "return".equals(((PsiDocTag)child).getName())) {
              return (PsiDocTag)child;
            }
          }
        }
      }
      return null;
    }
  }


  private class MyVisitor extends JavaElementVisitor {
    private final StringBuilder myBuffer;

    MyVisitor(@NotNull StringBuilder buffer) {
      myBuffer = buffer;
    }

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      appendStyledSpan(myBuffer, getHighlightingManager().getKeywordAttributes(), "new ");
      PsiType type = expression.getType();
      if (type != null) {
        generateType(myBuffer, type, expression);
      }
      PsiExpression[] dimensions = expression.getArrayDimensions();
      if (dimensions.length > 0) {
        int closeBracketIndex = myBuffer.length() - 1;
        while (closeBracketIndex > 0 && myBuffer.charAt(closeBracketIndex) != ']') closeBracketIndex--;
        LOG.assertTrue(myBuffer.charAt(closeBracketIndex) == ']');
        myBuffer.setLength(closeBracketIndex);
        for (int i = 0; i < dimensions.length; i++) {
          PsiExpression dimension = dimensions[i];
          dimension.accept(this);
          if (i + 1 != dimensions.length) appendStyledSpan(myBuffer, getHighlightingManager().getCommaAttributes(), ", ");
        }
        appendStyledSpan(myBuffer, getHighlightingManager().getBracketsAttributes(), "]");
      }
      else {
        expression.acceptChildren(this);
      }
    }

    @Override
    public void visitExpressionList(PsiExpressionList list) {
      appendStyledSpan(myBuffer, getHighlightingManager().getParenthesesAttributes(), "(");
      String separator = ", ";
      PsiExpression[] expressions = list.getExpressions();
      for (int i = 0; i < expressions.length; i++) {
        PsiExpression expression = expressions[i];
        expression.accept(this);
        if (i + 1 != expressions.length) appendStyledSpan(myBuffer, getHighlightingManager().getCommaAttributes(), separator);
      }
      appendStyledSpan(myBuffer, getHighlightingManager().getParenthesesAttributes(), ")");
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      appendStyledSpan(
        myBuffer,
        getHighlightingManager().getMethodCallAttributes(),
        StringUtil.escapeXmlEntities(expression.getMethodExpression().getText()));
      expression.getArgumentList().accept(this);
    }

    @Override
    public void visitExpression(PsiExpression expression) {
      appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
        doHighlightSignatures(), myBuffer, expression.getProject(), expression.getLanguage(), expression.getText());
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
        doHighlightSignatures(), myBuffer, expression.getProject(), expression.getLanguage(), expression.getText());
    }
  }

  public void generateTooltipAnnotations(PsiModifierListOwner owner, @Nls StringBuilder buffer) {
    generateAnnotations(buffer, owner, SignaturePlace.ToolTip, true, true, false);
  }

  private enum SignaturePlace {
    Javadoc, ToolTip
  }
}