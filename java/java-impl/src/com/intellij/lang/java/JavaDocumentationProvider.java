// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionMemory;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInsight.documentation.DocumentationManagerProtocol;
import com.intellij.codeInsight.documentation.PlatformDocumentationUtil;
import com.intellij.codeInsight.documentation.QuickDocUtil;
import com.intellij.codeInsight.editorActions.CodeDocumentationUtil;
import com.intellij.codeInsight.javadoc.*;
import com.intellij.ide.actions.FqnUtil;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.ide.util.PackageUtil;
import com.intellij.java.JavaBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.documentation.CodeDocumentationProvider;
import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.DocumentationSettings;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.beanProperties.BeanPropertyElement;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.scope.conflictResolvers.JavaMethodsConflictResolver;
import com.intellij.psi.util.*;
import com.intellij.util.SmartList;
import com.intellij.util.Url;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.*;
import org.jetbrains.builtInWebServer.BuiltInWebBrowserUrlProviderKt;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.intellij.lang.documentation.QuickDocHighlightingHelper.appendStyledSignatureFragment;
import static com.intellij.util.ObjectUtils.notNull;

/**
 * @author Maxim.Mossienko
 */
public class JavaDocumentationProvider implements CodeDocumentationProvider, ExternalDocumentationProvider {
  private static final Logger LOG = Logger.getInstance(JavaDocumentationProvider.class);

  private static final String LINE_SEPARATOR = "\n";
  private static final String PARAM_TAG = "@param";
  private static final String RETURN_TAG = "@return";
  private static final String THROWS_TAG = "@throws";

  public static final String HTML_EXTENSION = ".html";
  public static final String PACKAGE_SUMMARY_FILE = "package-summary.html";

  private static void appendStyledSpan(
    @NotNull StringBuilder buffer,
    @Nullable String value,
    String @NotNull ... properties
  ) {
    if (DocumentationSettings.isHighlightingOfQuickDocSignaturesEnabled()) {
      HtmlSyntaxInfoUtil.appendStyledSpan(buffer, value, properties);
    }
    else {
      buffer.append(value);
    }
  }

  @Override
  public @Nls String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    return QuickDocUtil.inferLinkFromFullDocumentation(this, element, originalElement,
                                                       getQuickNavigationInfoInner(element, originalElement));
  }

  private static @Nullable @Nls String getQuickNavigationInfoInner(PsiElement element, PsiElement originalElement) {
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
  public @Unmodifiable List<String> getUrlFor(final PsiElement element, final PsiElement originalElement) {
    return getExternalJavaDocUrl(element);
  }

  private static void newLine(StringBuilder buffer) {
    // Don't know why space has to be added after newline for good text alignment...
    buffer.append("\n ");
  }

  private static void generateInitializer(StringBuilder buffer, PsiVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    if (initializer != null) {
      JavaDocInfoGenerator generator = JavaDocInfoGeneratorFactory.create(variable.getProject(), null);
      generator.appendExpressionValue(buffer, initializer);
      PsiExpression constantInitializer = JavaDocInfoGenerator.calcInitializerExpression(variable);
      if (constantInitializer != null) {
        generator.appendExpressionValue(buffer, constantInitializer);
      }
    }
    else if (variable instanceof PsiEnumConstant enumConstant) {
      PsiExpressionList list = enumConstant.getArgumentList();
      if (JavaDocInfoGenerator.canComputeArguments(list)) {
        JavaDocInfoGenerator generator = JavaDocInfoGeneratorFactory.create(variable.getProject(), null);
        generator.generateExpressionText(enumConstant.getArgumentList(), buffer);
      }
    }
  }

  private static void generateModifiers(StringBuilder buffer, PsiModifierListOwner element) {
    JavaDocInfoGeneratorFactory.create(element.getProject(), null).generateModifiers(buffer, element, false);
  }

  private static @NlsSafe String generatePackageInfo(PsiPackage aPackage) {
    return aPackage.getQualifiedName();
  }

  private static void generateOrderEntryAndPackageInfo(StringBuilder buffer, @NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();

    if (file != null) {
      generateOrderEntryInfo(buffer, file.getVirtualFile(), element.getProject());
    }

    if (file instanceof PsiJavaFile) {
      String packageName = ((PsiJavaFile)file).getPackageName();
      if (!packageName.isEmpty()) {
        buffer.append(packageName);
        newLine(buffer);
      }
    }
  }

  private static void generateOrderEntryInfo(StringBuilder buffer, VirtualFile file, Project project) {
    if (file != null) {
      ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
      if (index.isInLibrary(file)) {
        index.getOrderEntriesForFile(file).stream()
          .filter(LibraryOrSdkOrderEntry.class::isInstance).findFirst()
          .ifPresent(
            entry -> appendStyledSpan(buffer, "[" + StringUtil.escapeXmlEntities(entry.getPresentableName()) + "] ", "color: #909090"));
      }
      else {
        Module module = index.getModuleForFile(file);
        if (module != null) {
          appendStyledSpan(buffer, "[" + module.getName() + "] ", "color: #909090");
        }
      }
    }
  }

  private static JavaDocInfoGenerator getDocInfoGenerator(@NotNull Project project, boolean isGenerationForRenderedDoc) {
    return JavaDocInfoGeneratorFactory.getBuilder(project).setIsGenerationForRenderedDoc(isGenerationForRenderedDoc).create();
  }

  public static @Nls String generateClassInfo(PsiClass aClass) {
    @Nls StringBuilder buffer = new StringBuilder();

    if (aClass instanceof PsiAnonymousClass) return JavaElementKind.ANONYMOUS_CLASS.subject();

    JavaDocInfoGenerator docInfoGenerator = getDocInfoGenerator(aClass.getProject(), false);

    generateOrderEntryAndPackageInfo(buffer, aClass);
    docInfoGenerator.generateTooltipAnnotations(aClass, buffer);
    generateModifiers(buffer, aClass);

    JavaDocHighlightingManagerImpl highlightingManager = JavaDocHighlightingManagerImpl.getInstance();

    final String classString = aClass.isAnnotationType() ? '@' + JavaKeywords.INTERFACE :
                               aClass.isInterface() ? JavaKeywords.INTERFACE :
                               aClass instanceof PsiTypeParameter ? JavaBundle.message("java.terms.type.parameter") :
                               aClass.isEnum() ? JavaKeywords.ENUM :
                               aClass.isRecord() ? JavaKeywords.RECORD :
                               JavaKeywords.CLASS;
    appendStyledSignatureFragment(buffer, classString, highlightingManager.getKeywordAttributes()).append(" ");

    appendStyledSignatureFragment(
      buffer,
      JavaDocUtil.getShortestClassName(aClass, aClass), highlightingManager.getClassDeclarationAttributes(aClass)
    );

    generateTypeParameters(aClass, buffer, highlightingManager);

    if (!aClass.isEnum() && !aClass.isAnnotationType()) {
      PsiReferenceList extendsList = aClass.getExtendsList();
      writeExtends(aClass, buffer, extendsList == null ? PsiClassType.EMPTY_ARRAY : extendsList.getReferencedTypes(), highlightingManager);
    }

    writeImplements(aClass, buffer, aClass.getImplementsListTypes(), highlightingManager);

    return buffer.toString();
  }

  public static void writeImplements(
    PsiClass aClass,
    StringBuilder buffer,
    PsiClassType[] refs,
    JavaDocHighlightingManager highlightingManager
  ) {
    if (refs.length > 0) {
      newLine(buffer);
      appendStyledSignatureFragment(buffer, "implements ", highlightingManager.getKeywordAttributes()); // <- Groovy keyword
      writeTypeRefs(aClass, buffer, refs, highlightingManager);
    }
  }

  public static void writeExtends(
    PsiClass aClass,
    StringBuilder buffer,
    PsiClassType[] refs,
    JavaDocHighlightingManager highlightingManager
  ) {
    if (refs.length > 0 || !aClass.isInterface() && !CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) {
      appendStyledSignatureFragment(buffer, " extends ", highlightingManager.getKeywordAttributes()); // <- Groovy keyword
      if (refs.length == 0) {
        appendStyledSignatureFragment(buffer, "Object", highlightingManager.getClassNameAttributes());
      }
      else {
        writeTypeRefs(aClass, buffer, refs, highlightingManager);
      }
    }
  }

  private static void writeTypeRefs(
    PsiClass aClass,
    StringBuilder buffer,
    PsiClassType[] refs,
    JavaDocHighlightingManager highlightingManager
  ) {
    for (int i = 0; i < refs.length; i++) {
      JavaDocInfoGeneratorFactory.getBuilder(aClass.getProject())
        .setIsGenerationForRenderedDoc(false)
        .setHighlightingManager(highlightingManager)
        .create()
        .generateType(buffer, refs[i], aClass, false, true);

      if (i < refs.length - 1) {
        appendStyledSignatureFragment(buffer, ", ", highlightingManager.getCommaAttributes());
      }
    }
  }

  public static void generateTypeParameters(
    PsiTypeParameterListOwner typeParameterOwner,
    StringBuilder buffer,
    JavaDocHighlightingManager highlightingManager
  ) {
    if (typeParameterOwner.hasTypeParameters()) {
      PsiTypeParameter[] params = typeParameterOwner.getTypeParameters();

      appendStyledSignatureFragment(buffer, "&lt;", highlightingManager.getOperationSignAttributes());

      for (int i = 0; i < params.length; i++) {
        PsiTypeParameter p = params[i];

        appendStyledSignatureFragment(buffer, p.getName(), highlightingManager.getTypeParameterNameAttributes());
        PsiClassType[] refs = p.getExtendsList().getReferencedTypes();

        if (refs.length > 0) {
          appendStyledSignatureFragment(buffer, " extends ", highlightingManager.getKeywordAttributes()); // <- Groovy keyword

          for (int j = 0; j < refs.length; j++) {
            JavaDocInfoGeneratorFactory.create(typeParameterOwner.getProject(), null)
              .generateType(buffer, refs[j], typeParameterOwner, false, true);

            if (j < refs.length - 1) {
              appendStyledSignatureFragment(buffer, " & ", highlightingManager.getOperationSignAttributes());
            }
          }
        }

        if (i < params.length - 1) {
          appendStyledSignatureFragment(buffer, ", ", highlightingManager.getCommaAttributes());
        }
      }

      appendStyledSignatureFragment(buffer, "&gt;", highlightingManager.getOperationSignAttributes());
    }
  }

  public static @NlsSafe String generateMethodInfo(PsiMethod method, PsiSubstitutor substitutor) {
    StringBuilder buffer = new StringBuilder();

    PsiClass parentClass = method.getContainingClass();

    JavaDocInfoGenerator docInfoGenerator = getDocInfoGenerator(method.getProject(), false);
    JavaDocHighlightingManagerImpl highlightingManager = JavaDocHighlightingManagerImpl.getInstance();

    if (parentClass != null && !(parentClass instanceof PsiAnonymousClass)) {
      if (method.isConstructor()) {
        generateOrderEntryAndPackageInfo(buffer, parentClass);
      }

      appendStyledSignatureFragment(
        buffer,
        JavaDocUtil.getShortestClassName(parentClass, method), highlightingManager.getClassDeclarationAttributes(parentClass)
      );
      newLine(buffer);
    }

    docInfoGenerator.generateTooltipAnnotations(method, buffer);
    generateModifiers(buffer, method);

    generateTypeParameters(method, buffer, highlightingManager);
    if (method.hasTypeParameters()) {
      buffer.append(" ");
    }

    if (method.getReturnType() != null) {
      docInfoGenerator.generateType(buffer, substitutor.substitute(method.getReturnType()), method, false, true);
      buffer.append(" ");
    }

    appendStyledSignatureFragment(buffer, method.getName(), highlightingManager.getMethodDeclarationAttributes(method));

    appendStyledSignatureFragment(buffer, "(", highlightingManager.getParenthesesAttributes());
    PsiParameter[] params = method.getParameterList().getParameters();
    for (int i = 0; i < params.length; i++) {
      PsiParameter param = params[i];
      docInfoGenerator.generateType(buffer, substitutor.substitute(param.getType()), method, false, true);
      buffer.append(" ");
      appendStyledSignatureFragment(buffer, param.getName(), highlightingManager.getParameterAttributes());
      if (i < params.length - 1) {
        appendStyledSignatureFragment(buffer, ", ", highlightingManager.getCommaAttributes());
      }
    }

    appendStyledSignatureFragment(buffer, ")", highlightingManager.getParenthesesAttributes());

    PsiClassType[] refs = method.getThrowsList().getReferencedTypes();
    if (refs.length > 0) {
      newLine(buffer);
      appendStyledSignatureFragment(buffer, " throws ", highlightingManager.getKeywordAttributes());
      for (int i = 0; i < refs.length; i++) {
        PsiClass throwsClass = refs[i].resolve();

        if (throwsClass != null) {
          appendStyledSignatureFragment(buffer, JavaDocUtil.getShortestClassName(throwsClass, method),
                                                                       highlightingManager.getClassDeclarationAttributes(throwsClass)
          );
        }
        else {
          buffer.append(refs[i].getPresentableText());
        }

        if (i < refs.length - 1) {
          appendStyledSignatureFragment(buffer, ", ", highlightingManager.getCommaAttributes());
        }
      }
    }

    return buffer.toString();
  }

  private static @Nls String generateFieldInfo(PsiField field, PsiSubstitutor substitutor) {
    @Nls StringBuilder buffer = new StringBuilder();
    PsiClass parentClass = field.getContainingClass();

    if (parentClass != null && !(parentClass instanceof PsiAnonymousClass)) {
      appendStyledSignatureFragment(
        buffer,
        JavaDocUtil.getShortestClassName(parentClass, field),
        JavaDocHighlightingManagerImpl.getInstance().getClassDeclarationAttributes(parentClass)
      );
      newLine(buffer);
    }

    JavaDocInfoGenerator docInfoGenerator = getDocInfoGenerator(field.getProject(), false);

    docInfoGenerator.generateTooltipAnnotations(field, buffer);
    generateModifiers(buffer, field);

    docInfoGenerator.generateType(buffer, substitutor.substitute(field.getType()), field, false, true);
    buffer.append(" ");
    appendStyledSignatureFragment(buffer, field.getName(),
                                                                 JavaDocHighlightingManagerImpl.getInstance().getFieldDeclarationAttributes(field));

    generateInitializer(buffer, field);

    JavaDocInfoGenerator.enumConstantOrdinal(buffer, field, parentClass, "\n");
    return buffer.toString();
  }

  private static @Nls String generateVariableInfo(PsiVariable variable) {
    @Nls StringBuilder buffer = new StringBuilder();

    generateModifiers(buffer, variable);

    JavaDocInfoGeneratorFactory.create(variable.getProject(), null)
      .generateType(buffer, variable.getType(), variable, false, true);

    buffer.append(" ");

    appendStyledSignatureFragment(buffer, variable.getName(), JavaDocHighlightingManagerImpl.getInstance().getLocalVariableAttributes());
    generateInitializer(buffer, variable);

    return buffer.toString();
  }

  private static @Nls String generateModuleInfo(PsiJavaModule module) {
    @Nls StringBuilder sb = new StringBuilder();

    VirtualFile file = PsiImplUtil.getModuleVirtualFile(module);
    generateOrderEntryInfo(sb, file, module.getProject());

    appendStyledSignatureFragment(sb, JavaKeywords.MODULE, JavaDocHighlightingManagerImpl.getInstance().getKeywordAttributes());
    sb.append(' ');
    appendStyledSignatureFragment(sb, module.getName(), JavaDocHighlightingManagerImpl.getInstance().getClassNameAttributes());

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

  @Override
  public @Nullable Pair<PsiElement, PsiComment> parseContext(@NotNull PsiElement startPoint) {
    PsiElement current = startPoint;
    while (current != null) {
      if (current instanceof PsiJavaDocumentedElement &&
          !(current instanceof PsiTypeParameter) &&
          !(current instanceof PsiAnonymousClass)) {
        PsiDocComment comment = ((PsiJavaDocumentedElement)current).getDocComment();
        return Pair.create(current, comment);
      }
      else if (PackageUtil.isPackageInfoFile(current)) {
        return Pair.create(current, getPackageInfoComment(current));
      }
      current = current.getParent();
    }
    return null;
  }

  private static Map<String, Object> collectContextAttributes(PsiJavaDocumentedElement commentOwner){
    HashMap<String, Object> attributes = new HashMap<>();
    if (commentOwner instanceof PsiMember member){
      String name = member.getName();
      if (name != null) {
        attributes.put("ELEMENT_NAME", name);
      }
      PsiClass containingClass = member.getContainingClass();
      String className = containingClass != null ? containingClass.getName() : null;
      if (className != null) {
        attributes.put("CONTAINING_CLASS", className);
      }
    }
    if (commentOwner instanceof PsiMethod method) {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      attributes.put("PARAMS", ContainerUtil.map(parameters, param -> param.getName()));
      attributes.put("TYPE_PARAMS", ContainerUtil.map(method.getTypeParameters(), param -> param.getName()));
      attributes.put("THROWS", ContainerUtil.map(method.getThrowsList().getReferenceElements(), reference -> reference.getText()));
      PsiTypeElement returnElement = method.getReturnTypeElement();
      if (returnElement != null) {
        attributes.put("RETURN_TYPE", String.valueOf(returnElement.getText()));
      }

      PsiMethod[] superMethods = method.findSuperMethods();
      if (superMethods.length > 0) {
        PsiMethod superMethod = superMethods[0];
        String qualifiedSuperMethod = FqnUtil.getQualifiedNameFromProviders(superMethod);
        if (qualifiedSuperMethod != null) {
          attributes.put("SUPER_METHOD", qualifiedSuperMethod);
        }
        Map<Integer, String> paramToParentDescription = collectParentParameterDescriptions(method, parameters);
        if (! paramToParentDescription.isEmpty()) {
          List<String> inheritedParams = IntStream
            .range(0, parameters.length)
            .mapToObj(i -> parameters[i].getName() + StringUtil.notNullize(paramToParentDescription.get(i)))
            .collect(Collectors.toList());
          attributes.put("PARAMS_INHERITED", inheritedParams);
        }
      }
    }
    if (commentOwner instanceof PsiClass psiClass) {
      attributes.put("TYPE_PARAMS", ContainerUtil.map(psiClass.getTypeParameters(), param -> param.getName()));
      if (psiClass.isInterface()){
        attributes.put("INTERFACE", String.valueOf(true));
      }
      if (psiClass.isEnum()){
        attributes.put("ENUM", String.valueOf(true));
      }
      if (psiClass.isRecord()){
        attributes.put("RECORD", String.valueOf(true));
        attributes.put("RECORD_COMPONENTS", ContainerUtil.map(psiClass.getRecordComponents(), param -> param.getName()));
      }
      String className = psiClass.getName();
      if (className != null) {
        attributes.put("CONTAINING_CLASS", psiClass.getName());
      }
    }
    return attributes;
  }

  private static @Nullable String generateStubFromTemplate(@Nullable PsiJavaDocumentedElement commentOwner) {
    if (commentOwner == null) return null;
    FileTemplate template = findTemplateByElement(commentOwner);
    if (template == null) return null;

    Map<String, Object> attributes = collectContextAttributes(commentOwner);
    Properties properties = FileTemplateManager.getInstance(commentOwner.getProject()).getDefaultProperties();
    for (String property : properties.stringPropertyNames()) {
      attributes.put(property, properties.getProperty(property));
    }
    try {
      return template.getText(attributes);
    }
    catch (IOException e) {
      LOG.trace(e);
      return null;
    }
  }

  private static @Nullable FileTemplate findTemplateByElement(PsiJavaDocumentedElement commentOwner) {
    String templateName = findTemplateNameByElement(commentOwner);
    if (templateName == null) return null;
    try {
      return FileTemplateManager.getInstance(commentOwner.getProject()).getCodeTemplate(templateName);
    }
    catch (IllegalStateException e) {
      return null;
    }
  }

  private static @Nullable @NonNls String findTemplateNameByElement(PsiJavaDocumentedElement commentOwner){
    if (commentOwner instanceof PsiMethod method) {
      if (method.isConstructor()) {
        return JavaTemplateUtil.TEMPLATE_JAVADOC_CONSTRUCTOR;
      }
      else if (method.findSuperMethods().length > 0) {
        return JavaTemplateUtil.TEMPLATE_JAVADOC_OVERRIDING_METHOD;
      }
      else {
        return JavaTemplateUtil.TEMPLATE_JAVADOC_METHOD;
      }
    }
    else if (commentOwner instanceof PsiField) {
      return JavaTemplateUtil.TEMPLATE_JAVADOC_FIELD;
    }
    else if (commentOwner instanceof PsiClass) {
      return JavaTemplateUtil.TEMPLATE_JAVADOC_CLASS;
    }
    return null;
  }

  @Override
  public String generateDocumentationContentStub(PsiComment _comment) {
    final PsiJavaDocumentedElement commentOwner = ((PsiDocComment)_comment).getOwner();
    if (Registry.is("java.javadoc.use.templates")) {
      String javaDocStub = generateStubFromTemplate(commentOwner);
      if (javaDocStub != null) return javaDocStub;
    }

    final StringBuilder builder = new StringBuilder();
    final CodeDocumentationAwareCommenter commenter =
      (CodeDocumentationAwareCommenter)LanguageCommenters.INSTANCE.forLanguage(_comment.getLanguage());
    if (commentOwner instanceof PsiMethod psiMethod) {
      generateParametersTakingDocFromSuperMethods(builder, commenter, psiMethod);

      final PsiTypeParameterList typeParameterList = psiMethod.getTypeParameterList();
      if (typeParameterList != null) {
        createTypeParamsListComment(builder, commenter, typeParameterList);
      }
      if (psiMethod.getReturnType() != null && !PsiTypes.voidType().equals(psiMethod.getReturnType())) {
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
      if (((PsiClass)commentOwner).isRecord()) {
        for (PsiRecordComponent component : ((PsiClass)commentOwner).getRecordComponents()) {
          builder.append(CodeDocumentationUtil.createDocCommentLine(PARAM_TAG, commentOwner.getContainingFile(), commenter));
          builder.append(component.getName());
          builder.append(LINE_SEPARATOR);
        }
      }
      final PsiTypeParameterList typeParameterList = ((PsiClass)commentOwner).getTypeParameterList();
      if (typeParameterList != null) {
        createTypeParamsListComment(builder, commenter, typeParameterList);
      }
    }
    return !builder.isEmpty() ? builder.toString() : null;
  }

  public static void generateParametersTakingDocFromSuperMethods(StringBuilder builder,
                                                                 CodeDocumentationAwareCommenter commenter,
                                                                 PsiMethod psiMethod) {
    PsiParameterList parameterList = psiMethod.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();
    final Map<Integer, String> index2Description = collectParentParameterDescriptions(psiMethod, parameters);

    for (int i = 0; i < parameters.length; i++) {
      builder.append(CodeDocumentationUtil.createDocCommentLine(PARAM_TAG, psiMethod.getContainingFile(), commenter));
      builder.append(parameters[i].getName());
      String description = index2Description.get(i);
      if (description != null) {
        builder.append(description);
      }
      builder.append(LINE_SEPARATOR);
    }
  }

  private static @NotNull Map<Integer, String> collectParentParameterDescriptions(PsiMethod psiMethod, PsiParameter[] parameters) {
    final Map<Integer, String> index2Description = new HashMap<>();

    for (int i = 0; i < parameters.length; i++) {
      PsiDocTag param = JavaDocInfoGenerator.findInheritDocTag(psiMethod, i);
      if (param == null) continue;
      final PsiElement[] dataElements = param.getDataElements();
      String paramName = null;
      int endOffset = 0;
      for (PsiElement dataElement : dataElements) {
        if (dataElement instanceof PsiDocParamRef) {
          //noinspection ConstantConditions
          paramName = dataElement.getReference().getCanonicalText();
          endOffset = dataElement.getTextRangeInParent().getEndOffset();
          break;
        }
      }
      if (paramName != null) {
        String description = param.getText().substring(endOffset).replaceFirst("(\\s*\\*)?\\s*$", "");
        index2Description.put(i, description);
      }
    }
    return index2Description;
  }

  public static void createTypeParamsListComment(final StringBuilder buffer,
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
  public @Nls String generateDoc(PsiElement element, PsiElement originalElement) {
    // for new Class(<caret>) or methodCall(<caret>) proceed from method call or new expression
    // same for new Cl<caret>ass() or method<caret>Call()
    if (element instanceof PsiExpressionList ||
        element instanceof PsiReferenceExpression && element.getParent() instanceof PsiMethodCallExpression) {
      element = element.getParent();
      originalElement = null;
    }
    if (element instanceof PsiCallExpression && CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION) {
      PsiMethod method = CompletionMemory.getChosenMethod((PsiCallExpression)element);
      if (method != null) element = method;
    }
    if (element instanceof PsiMethodCallExpression) {
      return getMethodCandidateInfo((PsiMethodCallExpression)element);
    }

    if (element instanceof FakePsiElement) {
      PsiDocCommentBase docCommentBase = PsiTreeUtil.getParentOfType(originalElement, PsiDocCommentBase.class);
      if (docCommentBase != null) {
        element = docCommentBase.getOwner();
      }
    }

    // Try hard for documentation of incomplete new Class instantiation
    PsiElement elt = element;
    if (originalElement != null && !(originalElement instanceof PsiPackage || originalElement instanceof PsiFileSystemItem)) {
      elt = PsiTreeUtil.prevLeaf(originalElement);
    }
    if (elt instanceof PsiErrorElement) {
      elt = elt.getPrevSibling();
    }
    else if (elt != null && !(elt instanceof PsiNewExpression)) {
      elt = elt.getParent();
    }
    if (elt instanceof PsiNewExpression) {
      PsiClass targetClass = null;

      if (element instanceof PsiJavaCodeReferenceElement) {     // new Class<caret>
        PsiElement resolve = ((PsiJavaCodeReferenceElement)element).resolve();
        if (resolve instanceof PsiClass) targetClass = (PsiClass)resolve;
      }
      else if (element instanceof PsiClass) { //Class in completion
        targetClass = (PsiClass)element;
      }
      else if (element instanceof PsiNewExpression) { // new Class(<caret>)
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

          for (PsiMethod constructor : constructors) {
            final String str = PsiFormatUtil.formatMethod(constructor, PsiSubstitutor.EMPTY,
                                                          PsiFormatUtilBase.SHOW_NAME |
                                                          PsiFormatUtilBase.SHOW_TYPE |
                                                          PsiFormatUtilBase.SHOW_PARAMETERS,
                                                          PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_NAME);
            createElementLink(sb, constructor, StringUtil.escapeXmlEntities(str));
          }

          return JavaBundle.message("javadoc.constructor.candidates", targetClass.getName(), sb);
        }
      }
    }

    //external documentation finder
    return generateExternalJavadoc(element);
  }

  @Override
  public @Nls @Nullable String generateHoverDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
    if (originalElement != null && PsiTreeUtil.isAncestor(element, originalElement, false)) {
      return null;
    }
    return generateDoc(element, originalElement);
  }

  @Override
  public @Nls @Nullable String generateRenderedDoc(@NotNull PsiDocCommentBase comment) {
    PsiElement target = comment.getOwner();
    if (target == null) target = comment;
    JavaDocInfoGenerator generator = JavaDocInfoGeneratorFactory.getBuilder(target.getProject())
      .setPsiElement(target)
      .setIsGenerationForRenderedDoc(true)
      .create();
    return JavaDocExternalFilter.filterInternalDocInfo(generator.generateRenderedDocInfo());
  }

  @Override
  public void collectDocComments(@NotNull PsiFile file, @NotNull Consumer<? super @NotNull PsiDocCommentBase> sink) {
    if (!(file instanceof PsiJavaFile)) return;
    if (file instanceof PsiCompiledElement) return;
    String fileName = file.getName();
    if (PsiPackage.PACKAGE_INFO_FILE.equals(fileName)) {
      PsiPackageStatement packageStatement = ((PsiJavaFile)file).getPackageStatement();
      if (packageStatement != null) {
        PsiElement prevElement = PsiTreeUtil.skipWhitespacesBackward(packageStatement);
        if (prevElement instanceof PsiDocCommentBase) {
          sink.accept((PsiDocCommentBase)prevElement);
        }
      }
    }
    else if (PsiJavaModule.MODULE_INFO_FILE.equals(fileName)) {
      PsiJavaModule module = ((PsiJavaFile)file).getModuleDeclaration();
      if (module != null) {
        collectDocComment(module, sink);
      }
    }
    else {
      PsiClass[] classes = ((PsiJavaFile)file).getClasses();
      for (PsiClass aClass : classes) {
        collectDocComments(aClass, sink);
      }
    }
  }

  private static void collectDocComments(@NotNull PsiClass aClass, @NotNull Consumer<? super @NotNull PsiDocCommentBase> sink) {
    collectDocComment(aClass, sink);
    List<PsiDocCommentOwner> children = PsiTreeUtil.getChildrenOfTypeAsList(aClass, PsiDocCommentOwner.class);
    for (PsiDocCommentOwner child : children) {
      if (child instanceof PsiClass) {
        collectDocComments((PsiClass)child, sink);
      }
      else {
        collectDocComment(child, sink);
      }
    }
  }

  private static void collectDocComment(@NotNull PsiJavaDocumentedElement commentOwner,
                                        @NotNull Consumer<? super @NotNull PsiDocCommentBase> sink) {
    PsiDocComment comment = commentOwner.getDocComment();
    if (comment != null) sink.accept(comment);
  }

  @Contract(value = "null -> null", pure = true)
  public static @Nullable String generateExternalJavadoc(final @Nullable PsiElement element) {
    if (element == null) return null;

    List<String> docURLs = getExternalJavaDocUrl(element);
    return generateExternalJavadoc(element, docURLs);
  }

  public static @Nullable String generateExternalJavadoc(final @NotNull PsiElement element, @Nullable List<String> docURLs) {
    final JavaDocInfoGenerator javaDocInfoGenerator = JavaDocInfoGeneratorFactory.create(element.getProject(), element);
    return generateExternalJavadoc(javaDocInfoGenerator, docURLs);
  }

  public static @Nullable @Nls String generateExternalJavadoc(final @NotNull PsiElement element, @NotNull JavaDocInfoGenerator generator) {
    final List<String> docURLs = getExternalJavaDocUrl(element);
    return generateExternalJavadoc(generator, docURLs);
  }

  private static @Nullable @Nls String generateExternalJavadoc(@NotNull JavaDocInfoGenerator generator, @Nullable List<String> docURLs) {
    return JavaDocExternalFilter.filterInternalDocInfo(generator.generateDocInfo(docURLs));
  }

  private @Nls String getMethodCandidateInfo(PsiMethodCallExpression expr) {
    final PsiResolveHelper rh = JavaPsiFacade.getInstance(expr.getProject()).getResolveHelper();
    final CandidateInfo[] candidates = rh.getReferencedMethodCandidates(expr, true);

    final String text = expr.getText();
    if (candidates.length > 0) {
      if (candidates.length == 1) {
        PsiElement element = candidates[0].getElement();
        if (element instanceof PsiMethod) return generateDoc(element, null);
      }
      final StringBuilder sb = new StringBuilder();
      @NotNull List<? extends CandidateInfo> conflicts = new ArrayList<>(Arrays.asList(candidates));
      JavaMethodsConflictResolver.filterSupers(conflicts, expr.getContainingFile(), null);

      for (final CandidateInfo candidate : conflicts) {
        final PsiElement element = candidate.getElement();

        if (!(element instanceof PsiMethod)) {
          continue;
        }

        final String str = PsiFormatUtil.formatMethod((PsiMethod)element, candidate.getSubstitutor(),
                                                      PsiFormatUtilBase.SHOW_NAME |
                                                      PsiFormatUtilBase.SHOW_TYPE |
                                                      PsiFormatUtilBase.SHOW_PARAMETERS,
                                                      PsiFormatUtilBase.SHOW_TYPE);
        createElementLink(sb, element, StringUtil.escapeXmlEntities(str));
      }

      return CodeInsightBundle.message("javadoc.candidates", text, sb);
    }

    return JavaBundle.message("javadoc.candidates.not.found", text);
  }

  private static void createElementLink(StringBuilder sb, PsiElement element, String str) {
    sb.append("&nbsp;&nbsp;<a href=\"" + DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL);
    sb.append(JavaDocUtil.getReferenceText(element.getProject(), element));
    sb.append("\">");
    sb.append(str);
    sb.append("</a>");
    sb.append("<br>");
  }

  public static @Unmodifiable @Nullable List<String> getExternalJavaDocUrl(final PsiElement element) {
    List<String> urls = null;

    if (element instanceof PsiClass) {
      urls = findUrlForClass((PsiClass)element);
    }
    else if (element instanceof PsiField field) {
      PsiClass aClass = field.getContainingClass();
      if (aClass != null) {
        urls = findUrlForClass(aClass);
        if (urls != null) {
          urls = ContainerUtil.map(urls, url -> url + "#" + field.getName());
        }
      }
    }
    else if (element instanceof PsiMethod method) {
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        List<String> classUrls = findUrlForClass(aClass);
        if (classUrls != null) {
          urls = new SmartList<>();
          final Set<String> signatures = getHtmlMethodSignatures(method, PsiUtil.getLanguageLevel(method));
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
      return ContainerUtil.map(urls, FileUtil::toSystemIndependentName);
    }
  }

  public static Set<String> getHtmlMethodSignatures(@NotNull PsiMethod method, @Nullable LanguageLevel preferredFormat) {
    final Set<String> signatures = new LinkedHashSet<>();
    if (preferredFormat != null) signatures.add(formatMethodSignature(method, preferredFormat));
    signatures.add(formatMethodSignature(method, LanguageLevel.JDK_10));
    signatures.add(formatMethodSignature(method, LanguageLevel.JDK_1_8));
    signatures.add(formatMethodSignature(method, LanguageLevel.JDK_1_5));
    return signatures;
  }

  private static String formatMethodSignature(@NotNull PsiMethod method, @NotNull LanguageLevel languageLevel) {
    boolean replaceConstructorWithInit = languageLevel.isAtLeast(LanguageLevel.JDK_10) && method.isConstructor();

    int options = (replaceConstructorWithInit ? 0 : PsiFormatUtilBase.SHOW_NAME) | PsiFormatUtilBase.SHOW_PARAMETERS;
    int parameterOptions = PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_FQ_CLASS_NAMES | PsiFormatUtilBase.SHOW_RAW_NON_TOP_TYPE;

    if (languageLevel.isAtLeast(LanguageLevel.JDK_15)) {
      parameterOptions |= PsiFormatUtilBase.SHOW_RAW_TYPE;
    }

    String signature = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, options, parameterOptions, 999);
    if (replaceConstructorWithInit) {
      signature = "<init>" + signature;
    }

    if (languageLevel.isAtLeast(LanguageLevel.JDK_10)) {
      signature = signature.replace(" ", "");
    }
    else if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
      signature = signature.replaceAll("\\(|\\)|, ", "-").replaceAll("\\[]", ":A");
    }

    return signature;
  }

  public static @Nullable PsiDocComment getPackageInfoComment(@NotNull PsiElement packageInfoFile) {
    return PsiTreeUtil.getChildOfType(packageInfoFile, PsiDocComment.class);
  }

  public static @Unmodifiable @Nullable List<String> findUrlForClass(@NotNull PsiClass aClass) {
    String qName = aClass.getQualifiedName();
    if (qName != null) {
      PsiFile file = aClass.getContainingFile();
      if (file instanceof PsiJavaFile) {
        VirtualFile virtualFile = file.getOriginalFile().getVirtualFile();
        if (virtualFile != null) {
          String pkgName = ((PsiJavaFile)file).getPackageName();
          if (!pkgName.isEmpty() && aClass instanceof PsiImplicitClass) return null;
          String relPath =
            (pkgName.isEmpty() ? qName : pkgName.replace('.', '/') + '/' + qName.substring(pkgName.length() + 1)) + HTML_EXTENSION;
          return findUrlForVirtualFile(file.getProject(), virtualFile, relPath);
        }
      }
    }

    return null;
  }

  public static @Nullable List<String> findUrlForPackage(@NotNull PsiPackage aPackage) {
    String qName = aPackage.getQualifiedName().replace('.', '/') + '/' + PACKAGE_SUMMARY_FILE;
    for (PsiDirectory directory : aPackage.getDirectories()) {
      List<String> url = findUrlForVirtualFile(aPackage.getProject(), directory.getVirtualFile(), qName);
      if (url != null) {
        return url;
      }
    }

    return null;
  }

  public static @Unmodifiable @Nullable List<String> findUrlForVirtualFile(Project project, VirtualFile virtualFile, String relPath) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    Module module = fileIndex.getModuleForFile(virtualFile);
    if (module == null) {
      VirtualFileSystem fs = virtualFile.getFileSystem();
      if (fs instanceof JarFileSystem) {
        VirtualFile jar = ((JarFileSystem)fs).getLocalByEntry(virtualFile);
        if (jar != null) {
          module = fileIndex.getModuleForFile(jar);
        }
      }
    }
    if (module != null) {
      String[] extPaths = JavaModuleExternalPaths.getInstance(module).getJavadocUrls();
      List<String> httpRoots = PlatformDocumentationUtil.getHttpRoots(extPaths, relPath);
      // if found nothing and the file is from library classes, fall back to order entries
      if (httpRoots != null || !fileIndex.isInLibraryClasses(virtualFile)) {
        return httpRoots;
      }
    }

    PsiJavaModule javaModule = JavaModuleGraphUtil.findDescriptorByFile(virtualFile, project);
    String altRelPath = javaModule != null ? javaModule.getName() + '/' + relPath : null;

    for (OrderEntry orderEntry : fileIndex.getOrderEntriesForFile(virtualFile)) {
      boolean altUrl =
        orderEntry instanceof JdkOrderEntry && JavaSdkVersionUtil.isAtLeast(((JdkOrderEntry)orderEntry).getJdk(), JavaSdkVersion.JDK_11);

      for (VirtualFile root : orderEntry.getFiles(JavadocOrderRootType.getInstance())) {
        if (root.getFileSystem() == JarFileSystem.getInstance()) {
          VirtualFile file = root.findFileByRelativePath(relPath);
          if (file == null && altRelPath != null) file = root.findFileByRelativePath(altRelPath);
          if (file != null) {
            List<Url> urls = BuiltInWebBrowserUrlProviderKt.getBuiltInServerUrls(file, project, null);
            if (!urls.isEmpty()) {
              return ContainerUtil.map(urls, Url::toExternalForm);
            }
          }
        }
      }

      String[] webUrls = JavadocOrderRootType.getUrls(orderEntry);
      if (webUrls.length > 0) {
        List<String> httpRoots = new ArrayList<>();
        if (altUrl && altRelPath != null) {
          httpRoots.addAll(notNull(PlatformDocumentationUtil.getHttpRoots(webUrls, altRelPath), Collections.emptyList()));
        }
        httpRoots.addAll(notNull(PlatformDocumentationUtil.getHttpRoots(webUrls, relPath), Collections.emptyList()));
        if (!httpRoots.isEmpty()) {
          return httpRoots;
        }
      }
    }

    return null;
  }

  @Override
  public PsiElement getDocumentationElementForLink(final PsiManager psiManager, final String link, final PsiElement context) {
    return JavaDocUtil.findReferenceTarget(psiManager, link, context);
  }

  @Override
  public @Nls String fetchExternalDocumentation(Project project, PsiElement element, List<String> docUrls, boolean onHover) {
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
  public void promptToConfigureDocumentation(PsiElement element) { }

  @Override
  public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file, @Nullable PsiElement contextElement,
                                                            int targetOffset) {
    PsiDocComment docComment = PsiTreeUtil.getParentOfType(contextElement, PsiDocComment.class, false);
    if (docComment != null && JavaDocUtil.isInsidePackageInfo(docComment)) {
      PsiDirectory directory = file.getContainingDirectory();
      if (directory != null) {
        return JavaDirectoryService.getInstance().getPackage(directory);
      }
    }
    return getDocumentedElementOfKeyword(contextElement);
  }

  /**
   * The method returns either class or interface or method or field or anything with a javadoc that is annotated with the keyword.
   * The method inspects keywords in the modifier lists, the class-level keywords (e.g. class, interface, implements, record, sealed, permits, etc.)
   * and primitive types
   *
   * @param contextElement element that is supposed to be a keyword
   * @return an instance of {@link PsiJavaDocumentedElement} that has javadoc if the keyword is either on the class or method or field level,
   * null otherwise
   */
  @Contract(value = "null -> null", pure = true)
  private static @Nullable PsiJavaDocumentedElement getDocumentedElementOfKeyword(final @Nullable PsiElement contextElement) {
    if (!(contextElement instanceof PsiKeyword)) return null;

    final PsiElement element =
      PsiTreeUtil.skipParentsOfType(contextElement, PsiModifierList.class, PsiReferenceList.class, PsiTypeElement.class);
    if (!(element instanceof PsiJavaDocumentedElement)) return null;

    return (PsiJavaDocumentedElement)element;
  }

  public static @NlsSafe String fetchExternalJavadoc(PsiElement element, Project project, List<String> docURLs) {
    return fetchExternalJavadoc(element, docURLs, new JavaDocExternalFilter(project));
  }

  public static @NlsSafe String fetchExternalJavadoc(PsiElement element, List<String> docURLs, @NotNull JavaDocExternalFilter docFilter) {
    if (docURLs != null) {
      for (String docURL : docURLs) {
        try {
          String externalDoc = docFilter.getExternalDocInfoForElement(docURL, element);
          if (!StringUtil.isEmpty(externalDoc)) {
            return externalDoc;
          }
        }
        catch (ProcessCanceledException ignored) {
          break;
        }
        catch (IndexNotReadyException e) {
          throw e;
        }
        catch (HttpRequests.HttpStatusException e) {
          LOG.info(e.getUrl() + ": " + e.getStatusCode());
        }
        catch (Exception e) {
          LOG.info(e);
        }
      }
    }
    return null;
  }
}