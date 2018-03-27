// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.lang.*;
import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettingsFacade;
import com.intellij.psi.impl.light.*;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class PsiElementFactoryImpl extends PsiJavaParserFacadeImpl implements PsiElementFactory {
  private final ConcurrentMap<LanguageLevel, PsiClass> myArrayClasses = ContainerUtil.newConcurrentMap();
  private final ConcurrentMap<GlobalSearchScope, PsiClassType> myCachedObjectType = ContainerUtil.newConcurrentMap();

  public PsiElementFactoryImpl(final PsiManagerEx manager) {
    super(manager);
    manager.registerRunnableToRunOnChange(() -> myCachedObjectType.clear());
  }

  @NotNull
  @Override
  public PsiClass getArrayClass(@NotNull LanguageLevel languageLevel) {
    return myArrayClasses.computeIfAbsent(languageLevel, this::createArrayClass);
  }

  private PsiClass createArrayClass(LanguageLevel level) {
    String text = level.isAtLeast(LanguageLevel.JDK_1_5) ?
                  "public class __Array__<T> {\n public final int length;\n public T[] clone() {}\n}" :
                  "public class __Array__{\n public final int length;\n public Object clone() {}\n}";
    PsiClass psiClass = ((PsiExtensibleClass)createClassFromText(text, null)).getOwnInnerClasses().get(0);
    ensureNonWritable(psiClass);
    PsiFile file = psiClass.getContainingFile();
    file.clearCaches();
    PsiUtil.FILE_LANGUAGE_LEVEL_KEY.set(file, level);
    return psiClass;
  }

  private static void ensureNonWritable(PsiClass arrayClass) {
    try {
      arrayClass.getContainingFile().getViewProvider().getVirtualFile().setWritable(false);
    }
    catch (IOException ignored) {}
  }

  @NotNull
  @Override
  public PsiClassType getArrayClassType(@NotNull final PsiType componentType, @NotNull final LanguageLevel languageLevel) {
    final PsiClass arrayClass = getArrayClass(languageLevel);
    final PsiTypeParameter[] typeParameters = arrayClass.getTypeParameters();

    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (typeParameters.length == 1) {
      substitutor = substitutor.put(typeParameters[0], componentType);
    }

    return createType(arrayClass, substitutor);
  }

  @NotNull
  @Override
  public PsiClassType createType(@NotNull PsiClass resolve, @NotNull PsiSubstitutor substitutor) {
    return new PsiImmediateClassType(resolve, substitutor);
  }

  @NotNull
  @Override
  public PsiClassType createType(@NotNull PsiClass resolve, @NotNull PsiSubstitutor substitutor, @Nullable LanguageLevel languageLevel) {
    return new PsiImmediateClassType(resolve, substitutor, languageLevel);
  }

  @NotNull
  @Override
  public PsiClassType createType(@NotNull PsiClass resolve,
                                 @NotNull PsiSubstitutor substitutor,
                                 @Nullable LanguageLevel languageLevel,
                                 @NotNull PsiAnnotation[] annotations) {
    return new PsiImmediateClassType(resolve, substitutor, languageLevel, annotations);
  }

  @NotNull
  @Override
  public PsiClass createClass(@NotNull final String name) throws IncorrectOperationException {
    return createClassInner("class", name);
  }

  @NotNull
  @Override
  public PsiClass createInterface(@NotNull final String name) throws IncorrectOperationException {
    return createClassInner("interface", name);
  }

  @NotNull
  @Override
  public PsiClass createEnum(@NotNull final String name) throws IncorrectOperationException {
    return createClassInner("enum", name);
  }

  @NotNull
  @Override
  public PsiClass createAnnotationType(@NotNull @NonNls String name) throws IncorrectOperationException {
    return createClassInner("@interface", name);
  }

  private PsiClass createClassInner(@NonNls final String type, @NonNls String name) {
    PsiUtil.checkIsIdentifier(myManager, name);
    final PsiJavaFile aFile = createDummyJavaFile("public " + type +  " " +  name +  " { }");
    final PsiClass[] classes = aFile.getClasses();
    if (classes.length != 1) {
      throw new IncorrectOperationException("Incorrect " + type + " name \"" + name + "\".");
    }
    return classes[0];
  }

  @NotNull
  @Override
  public PsiTypeElement createTypeElement(@NotNull final PsiType psiType) {
    final LightTypeElement element = new LightTypeElement(myManager, psiType);
    CodeEditUtil.setNodeGenerated(element.getNode(), true);
    return element;
  }

  @NotNull
  @Override
  public PsiJavaCodeReferenceElement createReferenceElementByType(@NotNull final PsiClassType type) {
    if (type instanceof PsiClassReferenceType) {
      return ((PsiClassReferenceType)type).getReference();
    }
    return new LightClassTypeReference(myManager, type);
  }

  @NotNull
  @Override
  public PsiTypeParameterList createTypeParameterList() {
    final PsiTypeParameterList parameterList = createMethodFromText("void foo()", null).getTypeParameterList();
    assert parameterList != null;
    return parameterList;
  }

  @NotNull
  @Override
  public PsiTypeParameter createTypeParameter(String name, PsiClassType[] superTypes) {
    @NonNls StringBuilder builder = new StringBuilder();
    builder.append("public <").append(name);
    if (superTypes.length > 1 ||
        superTypes.length == 1 && !superTypes[0].equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
      builder.append(" extends ");
      for (PsiClassType type : superTypes) {
        if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) continue;
        builder.append(type.getCanonicalText(true)).append('&');
      }

      builder.delete(builder.length() - 1, builder.length());
    }
    builder.append("> void foo(){}");
    try {
      return createMethodFromText(builder.toString(), null).getTypeParameters()[0];
    }
    catch (RuntimeException e) {
      throw new IncorrectOperationException("type parameter text: " + builder.toString());
    }
  }

  @NotNull
  @Override
  public PsiField createField(@NotNull final String name, @NotNull final PsiType type) throws IncorrectOperationException {
    PsiUtil.checkIsIdentifier(myManager, name);
    if (PsiType.NULL.equals(type)) {
      throw new IncorrectOperationException("Cannot create field with type \"null\".");
    }

    @NonNls final String text = "class _Dummy_ { private " + GenericsUtil.getVariableTypeByExpressionType(type).getCanonicalText(true) + " " + name + "; }";
    final PsiJavaFile aFile = createDummyJavaFile(text);
    final PsiClass[] classes = aFile.getClasses();
    if (classes.length < 1) {
      throw new IncorrectOperationException("Class was not created " + text);
    }
    final PsiClass psiClass = classes[0];
    final PsiField[] fields = psiClass.getFields();
    if (fields.length < 1) {
      throw new IncorrectOperationException("Field was not created " + text);
    }
    PsiField field = fields[0];
    field = (PsiField)JavaCodeStyleManager.getInstance(myManager.getProject()).shortenClassReferences(field);
    return (PsiField)CodeStyleManager.getInstance(myManager.getProject()).reformat(field);
  }

  @NotNull
  @Override
  public PsiMethod createMethod(@NotNull final String name, final PsiType returnType) throws IncorrectOperationException {
    PsiUtil.checkIsIdentifier(myManager, name);
    if (PsiType.NULL.equals(returnType)) {
      throw new IncorrectOperationException("Cannot create method with type \"null\".");
    }

    final String canonicalText = GenericsUtil.getVariableTypeByExpressionType(returnType).getCanonicalText(true);
    final PsiJavaFile aFile = createDummyJavaFile("class _Dummy_ { public " + canonicalText + " " + name + "() {\n} }");
    final PsiClass[] classes = aFile.getClasses();
    if (classes.length < 1) {
      throw new IncorrectOperationException("Class was not created. Method name: " + name + "; return type: " + canonicalText);
    }
    final PsiMethod[] methods = classes[0].getMethods();
    if (methods.length < 1) {
      throw new IncorrectOperationException("Method was not created. Method name: " + name + "; return type: " + canonicalText);
    }
    PsiMethod method = methods[0];
    method = (PsiMethod)JavaCodeStyleManager.getInstance(myManager.getProject()).shortenClassReferences(method);
    return (PsiMethod)CodeStyleManager.getInstance(myManager.getProject()).reformat(method);
  }

  @NotNull
  @Override
  public PsiMethod createMethod(@NotNull @NonNls String name, PsiType returnType, PsiElement context) throws IncorrectOperationException {
    return createMethodFromText("public " + GenericsUtil.getVariableTypeByExpressionType(returnType).getCanonicalText(true) + " " + name + "() {}", context);
  }

  @NotNull
  @Override
  public PsiMethod createConstructor() {
    return createConstructor("_Dummy_");
  }

  @NotNull
  @Override
  public PsiMethod createConstructor(@NotNull @NonNls final String name) {
    final PsiJavaFile aFile = createDummyJavaFile("class " + name + " { public " + name + "() {} }");
    final PsiMethod method = aFile.getClasses()[0].getMethods()[0];
    return (PsiMethod)CodeStyleManager.getInstance(myManager.getProject()).reformat(method);
  }

  @Override
  public PsiMethod createConstructor(@NotNull @NonNls String name, PsiElement context) {
    return createMethodFromText(name + "() {}", context);
  }

  @NotNull
  @Override
  public PsiClassInitializer createClassInitializer() throws IncorrectOperationException {
    final PsiJavaFile aFile = createDummyJavaFile("class _Dummy_ { {} }");
    final PsiClassInitializer classInitializer = aFile.getClasses()[0].getInitializers()[0];
    return (PsiClassInitializer)CodeStyleManager.getInstance(myManager.getProject()).reformat(classInitializer);
  }

  @NotNull
  @Override
  public PsiParameter createParameter(@NotNull final String name, @NotNull final PsiType type) throws IncorrectOperationException {
    PsiUtil.checkIsIdentifier(myManager, name);
    if (PsiType.NULL.equals(type)) {
      throw new IncorrectOperationException("Cannot create parameter with type \"null\".");
    }

    final String text = type.getCanonicalText(true) + " " + name;
    PsiParameter parameter = createParameterFromText(text, null);
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myManager.getProject());
    PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL,
                                JavaCodeStyleSettingsFacade.getInstance(myManager.getProject()).isGenerateFinalParameters());
    GeneratedMarkerVisitor.markGenerated(parameter);
    parameter = (PsiParameter)JavaCodeStyleManager.getInstance(myManager.getProject()).shortenClassReferences(parameter);
    return (PsiParameter)codeStyleManager.reformat(parameter);
  }

  @Override
  public PsiParameter createParameter(@NotNull @NonNls String name, PsiType type, PsiElement context) throws IncorrectOperationException {
    final PsiMethod psiMethod = createMethodFromText("void f(" + type.getCanonicalText(true) + " " + name + ") {}", context);
    final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
    return parameters[0];
  }

  @NotNull
  @Override
  public PsiCodeBlock createCodeBlock() {
    final PsiCodeBlock block = createCodeBlockFromText("{}", null);
    return (PsiCodeBlock)CodeStyleManager.getInstance(myManager.getProject()).reformat(block);
  }

  @NotNull
  @Override
  public PsiClassType createType(@NotNull final PsiClass aClass) {
    return new PsiImmediateClassType(aClass, aClass instanceof PsiTypeParameter ? PsiSubstitutor.EMPTY : createRawSubstitutor(aClass));
  }

  @NotNull
  @Override
  public PsiClassType createType(@NotNull final PsiJavaCodeReferenceElement classReference) {
    return new PsiClassReferenceType(classReference, null);
  }

  @NotNull
  @Override
  public PsiClassType createType(@NotNull final PsiClass aClass, final PsiType parameter) {
    final PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
    assert typeParameters.length == 1 : aClass;

    return createType(aClass, PsiSubstitutor.EMPTY.put(typeParameters[0], parameter));
  }

  @NotNull
  @Override
  public PsiClassType createType(@NotNull final PsiClass aClass, final PsiType... parameters) {
    return createType(aClass, PsiSubstitutor.EMPTY.putAll(aClass, parameters));
  }

  @NotNull
  @Override
  public PsiSubstitutor createRawSubstitutor(@NotNull final PsiTypeParameterListOwner owner) {
    Map<PsiTypeParameter, PsiType> substitutorMap = null;
    for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(owner)) {
      if (substitutorMap == null) substitutorMap = new HashMap<>();
      substitutorMap.put(parameter, null);
    }
    return PsiSubstitutorImpl.createSubstitutor(substitutorMap);
  }

  @NotNull
  @Override
  public PsiSubstitutor createRawSubstitutor(@NotNull final PsiSubstitutor baseSubstitutor, @NotNull final PsiTypeParameter[] typeParameters) {
    Map<PsiTypeParameter, PsiType> substitutorMap = null;
    for (PsiTypeParameter parameter : typeParameters) {
      if (substitutorMap == null) substitutorMap = new HashMap<>();
      substitutorMap.put(parameter, null);
    }
    return PsiSubstitutorImpl.createSubstitutor(substitutorMap).putAll(baseSubstitutor);
  }

  @NotNull
  @Override
  public PsiElement createDummyHolder(@NotNull final String text, @NotNull final IElementType type, @Nullable final PsiElement context) {
    final DummyHolder result = DummyHolderFactory.createHolder(myManager, context);
    final FileElement holder = result.getTreeElement();
    final Language language = type.getLanguage();
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
    assert parserDefinition != null : "No parser definition for language " + language;
    final Project project = myManager.getProject();
    final Lexer lexer = parserDefinition.createLexer(project);
    final PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(project, holder, lexer, language, text);
    final ASTNode node = parserDefinition.createParser(project).parse(type, builder);
    holder.rawAddChildren((TreeElement)node);
    final PsiElement psi = node.getPsi();
    assert psi != null : text;
    return psi;
  }

  @NotNull
  @Override
  public PsiSubstitutor createSubstitutor(@NotNull final Map<PsiTypeParameter, PsiType> map) {
    return PsiSubstitutorImpl.createSubstitutor(map);
  }

  @Nullable
  @Override
  public PsiPrimitiveType createPrimitiveType(@NotNull final String text) {
    return PsiJavaParserFacadeImpl.getPrimitiveType(text);
  }

  @NotNull
  @Override
  public PsiClassType createTypeByFQClassName(@NotNull final String qName) {
    return createTypeByFQClassName(qName, GlobalSearchScope.allScope(myManager.getProject()));
  }

  @NotNull
  @Override
  public PsiClassType createTypeByFQClassName(@NotNull final String qName, @NotNull final GlobalSearchScope resolveScope) {
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(qName)) {
      PsiClassType cachedObjectType = myCachedObjectType.get(resolveScope);
      if (cachedObjectType != null) {
        return cachedObjectType;
      }
      PsiClass aClass = JavaPsiFacade.getInstance(myManager.getProject()).findClass(CommonClassNames.JAVA_LANG_OBJECT, resolveScope);
      if (aClass != null) {
        cachedObjectType = new PsiImmediateClassType(aClass, PsiSubstitutor.EMPTY);
        cachedObjectType = ConcurrencyUtil.cacheOrGet(myCachedObjectType, resolveScope, cachedObjectType);
        return cachedObjectType;
      }
    }
    return new PsiClassReferenceType(createReferenceElementByFQClassName(qName, resolveScope), null);
  }

  @NotNull
  @Override
  public PsiJavaCodeReferenceElement createClassReferenceElement(@NotNull final PsiClass aClass) {
    final String text;
    if (aClass instanceof PsiAnonymousClass) {
      text = ((PsiAnonymousClass)aClass).getBaseClassType().getPresentableText();
    }
    else {
      text = aClass.getName();
    }
    if (text == null) {
      throw new IncorrectOperationException("Invalid class: " + aClass);
    }
    return new LightClassReference(myManager, text, aClass);
  }

  @NotNull
  @Override
  public PsiJavaCodeReferenceElement createReferenceElementByFQClassName(@NotNull final String qName,
                                                                         @NotNull final GlobalSearchScope resolveScope) {
    final String shortName = PsiNameHelper.getShortClassName(qName);
    return new LightClassReference(myManager, shortName, qName, resolveScope);
  }

  @NotNull
  @Override
  public PsiJavaCodeReferenceElement createFQClassNameReferenceElement(@NotNull final String qName,
                                                                       @NotNull final GlobalSearchScope resolveScope) {
    return new LightClassReference(myManager, qName, qName, resolveScope);
  }

  @NotNull
  @Override
  public PsiJavaCodeReferenceElement createPackageReferenceElement(@NotNull final PsiPackage aPackage) throws IncorrectOperationException {
    if (aPackage.getQualifiedName().isEmpty()) {
      throw new IncorrectOperationException("Cannot create reference to default package.");
    }
    return new LightPackageReference(myManager, aPackage);
  }

  @NotNull
  @Override
  public PsiPackageStatement createPackageStatement(@NotNull final String name) throws IncorrectOperationException {
    final PsiJavaFile aFile = createDummyJavaFile("package " + name + ";");
    final PsiPackageStatement stmt = aFile.getPackageStatement();
    if (stmt == null) {
      throw new IncorrectOperationException("Incorrect package name: " + name);
    }
    return stmt;
  }

  @NotNull
  @Override
  public PsiImportStaticStatement createImportStaticStatement(@NotNull final PsiClass aClass,
                                                              @NotNull final String memberName) throws IncorrectOperationException {
    if (aClass instanceof PsiAnonymousClass) {
      throw new IncorrectOperationException("Cannot create import statement for anonymous class.");
    }
    else if (aClass.getParent() instanceof PsiDeclarationStatement) {
      throw new IncorrectOperationException("Cannot create import statement for local class.");
    }

    final PsiJavaFile aFile = createDummyJavaFile("import static " + aClass.getQualifiedName() + "." + memberName + ";");
    final PsiImportStatementBase statement = extractImport(aFile, true);
    return (PsiImportStaticStatement)CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
  }

  @NotNull
  @Override
  public PsiParameterList createParameterList(@NotNull final String[] names, @NotNull final PsiType[] types) throws IncorrectOperationException {
    @NonNls StringBuilder builder = new StringBuilder();
    builder.append("void method(");
    for (int i = 0; i < names.length; i++) {
      if (i > 0) builder.append(", ");
      builder.append(types[i].getCanonicalText(true)).append(' ').append(names[i]);
    }
    builder.append(");");
    return createMethodFromText(builder.toString(), null).getParameterList();
  }

  @NotNull
  @Override
  public PsiReferenceList createReferenceList(@NotNull final PsiJavaCodeReferenceElement[] references) throws IncorrectOperationException {
    @NonNls final StringBuilder builder = new StringBuilder();
    builder.append("void method()");
    if (references.length > 0){
      builder.append(" throws ");
      for (int i = 0; i < references.length; i++) {
        if (i > 0) builder.append(", ");
        builder.append(references[i].getCanonicalText());
      }
    }
    builder.append(';');
    return createMethodFromText(builder.toString(), null).getThrowsList();
  }

  @NotNull
  @Override
  public PsiJavaCodeReferenceElement createPackageReferenceElement(@NotNull final String packageName) throws IncorrectOperationException {
    if (packageName.isEmpty()) {
      throw new IncorrectOperationException("Cannot create reference to default package.");
    }
    return new LightPackageReference(myManager, packageName);
  }

  @NotNull
  @Override
  public PsiReferenceExpression createReferenceExpression(@NotNull final PsiClass aClass) throws IncorrectOperationException {
    final String text;
    if (aClass instanceof PsiAnonymousClass) {
      text = ((PsiAnonymousClass)aClass).getBaseClassType().getPresentableText();
    }
    else {
      text = aClass.getName();
    }
    return new LightClassReferenceExpression(myManager, text, aClass);
  }

  @NotNull
  @Override
  public PsiReferenceExpression createReferenceExpression(@NotNull final PsiPackage aPackage) throws IncorrectOperationException {
    if (aPackage.getQualifiedName().isEmpty()) {
      throw new IncorrectOperationException("Cannot create reference to default package.");
    }
    return new LightPackageReferenceExpression(myManager, aPackage);
  }

  @NotNull
  @Override
  public PsiIdentifier createIdentifier(@NotNull final String text) throws IncorrectOperationException {
    PsiUtil.checkIsIdentifier(myManager, text);
    return new LightIdentifier(myManager, text);
  }

  @NotNull
  @Override
  public PsiKeyword createKeyword(@NotNull final String text) throws IncorrectOperationException {
    if (!PsiNameHelper.getInstance(myManager.getProject()).isKeyword(text)) {
      throw new IncorrectOperationException("\"" + text + "\" is not a keyword.");
    }
    return new LightKeyword(myManager, text);
  }

  @NotNull
  @Override
  public PsiKeyword createKeyword(@NotNull @NonNls String keyword, PsiElement context) throws IncorrectOperationException {
    LanguageLevel level = PsiUtil.getLanguageLevel(context);
    if (!JavaLexer.isKeyword(keyword, level) && !JavaLexer.isSoftKeyword(keyword, level)) {
      throw new IncorrectOperationException("\"" + keyword + "\" is not a keyword.");
    }
    return new LightKeyword(myManager, keyword);
  }

  @NotNull
  @Override
  public PsiImportStatement createImportStatement(@NotNull final PsiClass aClass) throws IncorrectOperationException {
    if (aClass instanceof PsiAnonymousClass) {
      throw new IncorrectOperationException("Cannot create import statement for anonymous class.");
    }
    else if (aClass.getParent() instanceof PsiDeclarationStatement) {
      throw new IncorrectOperationException("Cannot create import statement for local class.");
    }

    final PsiJavaFile aFile = createDummyJavaFile("import " + aClass.getQualifiedName() + ";");
    final PsiImportStatementBase statement = extractImport(aFile, false);
    return (PsiImportStatement)CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
  }

  @NotNull
  @Override
  public PsiImportStatement createImportStatementOnDemand(@NotNull final String packageName) throws IncorrectOperationException {
    if (packageName.isEmpty()) {
      throw new IncorrectOperationException("Cannot create import statement for default package.");
    }
    if (!PsiNameHelper.getInstance(myManager.getProject()).isQualifiedName(packageName)) {
      throw new IncorrectOperationException("Incorrect package name: \"" + packageName + "\".");
    }

    final PsiJavaFile aFile = createDummyJavaFile("import " + packageName + ".*;");
    final PsiImportStatementBase statement = extractImport(aFile, false);
    return (PsiImportStatement)CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
  }

  @NotNull
  @Override
  public PsiDeclarationStatement createVariableDeclarationStatement(@NonNls @NotNull String name,
                                                                    @NotNull PsiType type,
                                                                    @Nullable PsiExpression initializer) throws IncorrectOperationException {
    return createVariableDeclarationStatement(name, type, initializer, null);
  }

  @NotNull
  @Override
  public PsiDeclarationStatement createVariableDeclarationStatement(@NonNls @NotNull String name,
                                                                    @NotNull PsiType type,
                                                                    @Nullable PsiExpression initializer,
                                                                    @Nullable PsiElement context) throws IncorrectOperationException {
    if (!isIdentifier(name)) {
      throw new IncorrectOperationException("\"" + name + "\" is not an identifier.");
    }
    if (PsiType.NULL.equals(type)) {
      throw new IncorrectOperationException("Cannot create variable with type \"null\".");
    }

    String text = "X " + name + (initializer != null ? " = x" : "") + ";";
    PsiDeclarationStatement statement = (PsiDeclarationStatement)createStatementFromText(text, context);

    PsiVariable variable = (PsiVariable)statement.getDeclaredElements()[0];
    replace(variable.getTypeElement(), createTypeElement(GenericsUtil.getVariableTypeByExpressionType(type)), text);

    boolean generateFinalLocals = JavaCodeStyleSettingsFacade.getInstance(myManager.getProject()).isGenerateFinalLocals();
    PsiUtil.setModifierProperty(variable, PsiModifier.FINAL, generateFinalLocals);

    if (initializer != null) {
      replace(variable.getInitializer(), initializer, text);
    }

    GeneratedMarkerVisitor.markGenerated(statement);
    return statement;
  }

  private static void replace(@Nullable PsiElement original, @NotNull PsiElement replacement, @NotNull String message) {
    assert original != null : message;
    original.replace(replacement);
  }

  @NotNull
  @Override
  public PsiDocTag createParamTag(@NotNull final String parameterName, @NonNls final String description) throws IncorrectOperationException {
    @NonNls final StringBuilder builder = new StringBuilder();
    builder.append(" * @param ");
    builder.append(parameterName);
    builder.append(" ");
    final String[] strings = description.split("\\n");
    for (int i = 0; i < strings.length; i++) {
      if (i > 0) builder.append("\n * ");
      builder.append(strings[i]);
    }
    return createDocTagFromText(builder.toString());
  }

  @NotNull
  @Override
  public PsiAnnotation createAnnotationFromText(@NotNull final String annotationText, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiAnnotation psiAnnotation = super.createAnnotationFromText(annotationText, context);
    GeneratedMarkerVisitor.markGenerated(psiAnnotation);
    return psiAnnotation;
  }

  public PsiAnnotation createAnnotationFromText(@NotNull final String annotationText,
                                                @Nullable final PsiElement context,
                                                boolean markGenerated) throws IncorrectOperationException {
    final PsiAnnotation psiAnnotation = super.createAnnotationFromText(annotationText, context);
    if (markGenerated) {
      GeneratedMarkerVisitor.markGenerated(psiAnnotation);
    }
    return psiAnnotation;
  }

  @NotNull
  @Override
  public PsiCodeBlock createCodeBlockFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiCodeBlock psiCodeBlock = super.createCodeBlockFromText(text, context);
    GeneratedMarkerVisitor.markGenerated(psiCodeBlock);
    return psiCodeBlock;
  }

  @NotNull
  @Override
  public PsiEnumConstant createEnumConstantFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiEnumConstant enumConstant = super.createEnumConstantFromText(text, context);
    GeneratedMarkerVisitor.markGenerated(enumConstant);
    return enumConstant;
  }

  @NotNull
  @Override
  public PsiExpression createExpressionFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiExpression expression = super.createExpressionFromText(text, context);
    GeneratedMarkerVisitor.markGenerated(expression);
    return expression;
  }

  @NotNull
  @Override
  public PsiField createFieldFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiField psiField = super.createFieldFromText(text, context);
    GeneratedMarkerVisitor.markGenerated(psiField);
    return psiField;
  }

  @NotNull
  @Override
  public PsiParameter createParameterFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiParameter parameter = super.createParameterFromText(text, context);
    GeneratedMarkerVisitor.markGenerated(parameter);
    return parameter;
  }

  @NotNull
  @Override
  public PsiStatement createStatementFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiStatement statement = super.createStatementFromText(text, context);
    GeneratedMarkerVisitor.markGenerated(statement);
    return statement;
  }

  @NotNull
  @Override
  public PsiType createTypeFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    return createTypeInner(text, context, true);
  }

  @NotNull
  @Override
  public PsiTypeParameter createTypeParameterFromText(@NotNull final String text,
                                                      final PsiElement context) throws IncorrectOperationException {
    final PsiTypeParameter typeParameter = super.createTypeParameterFromText(text, context);
    GeneratedMarkerVisitor.markGenerated(typeParameter);
    return typeParameter;
  }

  @NotNull
  @Override
  public PsiMethod createMethodFromText(@NotNull final String text,
                                        final PsiElement context,
                                        final LanguageLevel level) throws IncorrectOperationException {
    final PsiMethod method = super.createMethodFromText(text, context, level);
    GeneratedMarkerVisitor.markGenerated(method);
    return method;
  }

  private static PsiImportStatementBase extractImport(final PsiJavaFile aFile, final boolean isStatic) {
    final PsiImportList importList = aFile.getImportList();
    assert importList != null : aFile;
    final PsiImportStatementBase[] statements = isStatic ? importList.getImportStaticStatements() : importList.getImportStatements();
    assert statements.length == 1 : aFile.getText();
    return statements[0];
  }

  private static final JavaParserUtil.ParserWrapper CATCH_SECTION = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getStatementParser().parseCatchBlock(builder);
    }
  };

  @NotNull
  @Override
  public PsiCatchSection createCatchSection(@NotNull final PsiType exceptionType,
                                            @NotNull final String exceptionName,
                                            @Nullable final PsiElement context) throws IncorrectOperationException {
    if (!(exceptionType instanceof PsiClassType || exceptionType instanceof PsiDisjunctionType)) {
      throw new IncorrectOperationException("Unexpected type:" + exceptionType);
    }

    @NonNls final String text = "catch (" + exceptionType.getCanonicalText(true) +  " " + exceptionName + ") {}";
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, CATCH_SECTION, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiCatchSection)) {
      throw new IncorrectOperationException("Incorrect catch section '" + text + "'. Parsed element: " + element);
    }

    final Project project = myManager.getProject();
    final JavaPsiImplementationHelper helper = JavaPsiImplementationHelper.getInstance(project);
    helper.setupCatchBlock(exceptionName, exceptionType, context, (PsiCatchSection)element);
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
    final PsiCatchSection catchSection = (PsiCatchSection)styleManager.reformat(element);

    GeneratedMarkerVisitor.markGenerated(catchSection);
    return catchSection;
  }

  @Override
  public boolean isValidClassName(@NotNull String name) {
    return isIdentifier(name);
  }

  @Override
  public boolean isValidMethodName(@NotNull String name) {
    return isIdentifier(name);
  }

  @Override
  public boolean isValidParameterName(@NotNull String name) {
    return isIdentifier(name);
  }

  @Override
  public boolean isValidFieldName(@NotNull String name) {
    return isIdentifier(name);
  }

  @Override
  public boolean isValidLocalVariableName(@NotNull String name) {
    return isIdentifier(name);
  }

  private boolean isIdentifier(@NotNull String name) {
    return PsiNameHelper.getInstance(myManager.getProject()).isIdentifier(name);
  }
}