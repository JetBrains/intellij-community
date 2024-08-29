// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.lang.*;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettingsFacade;
import com.intellij.psi.codeStyle.JavaFileCodeStyleFacade;
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
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.psi.impl.PsiManagerImpl.ANY_PSI_CHANGE_TOPIC;

public final class PsiElementFactoryImpl extends PsiJavaParserFacadeImpl implements PsiElementFactory, Disposable {
  private final ConcurrentMap<LanguageLevel, PsiClass> myArrayClasses = new ConcurrentHashMap<>();
  private final ConcurrentMap<GlobalSearchScope, PsiClassType> myCachedObjectType = CollectionFactory.createConcurrentSoftMap();
  private static final Key<Boolean> ARRAY_CLASS = Key.create("JavaSyntheticArrayClass");

  public PsiElementFactoryImpl(@NotNull Project project) {
    super(project);
    project.getMessageBus().connect(this).subscribe(ANY_PSI_CHANGE_TOPIC, new AnyPsiChangeListener() {
      @Override
      public void beforePsiChanged(boolean isPhysical) {
        if (isPhysical) myCachedObjectType.clear();
      }
    });
  }

  @Override
  public @NotNull PsiClass getArrayClass(@NotNull LanguageLevel languageLevel) {
    return myArrayClasses.computeIfAbsent(languageLevel, this::createArrayClass);
  }

  private PsiClass createArrayClass(LanguageLevel level) {
    String text = JavaFeature.GENERICS.isSufficient(level) ?
                  "public static class __Array__<T> {\n public final int length;\n public T[] clone() {}\n}" :
                  "public static class __Array__{\n public final int length;\n public Object clone() {}\n}";
    PsiClass psiClass = ((PsiExtensibleClass)createClassFromText(text, null)).getOwnInnerClasses().get(0);
    ensureNonWritable(psiClass);
    PsiFile file = psiClass.getContainingFile();
    file.clearCaches();
    PsiUtil.FILE_LANGUAGE_LEVEL_KEY.set(file, level);
    ARRAY_CLASS.set(psiClass, true);
    return psiClass;
  }

  @Override
  public boolean isArrayClass(@NotNull PsiClass psiClass) {
    return Boolean.TRUE.equals(ARRAY_CLASS.get(psiClass));
  }

  private static void ensureNonWritable(PsiClass arrayClass) {
    try {
      arrayClass.getContainingFile().getViewProvider().getVirtualFile().setWritable(false);
    }
    catch (IOException ignored) {}
  }

  @Override
  public @NotNull PsiClassType getArrayClassType(@NotNull PsiType componentType, @NotNull LanguageLevel languageLevel) {
    PsiClass arrayClass = getArrayClass(languageLevel);
    PsiTypeParameter[] typeParameters = arrayClass.getTypeParameters();

    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (typeParameters.length == 1) {
      substitutor = substitutor.put(typeParameters[0], componentType);
    }

    return createType(arrayClass, substitutor);
  }

  @Override
  public @NotNull PsiClassType createType(@NotNull PsiClass resolve, @NotNull PsiSubstitutor substitutor) {
    return new PsiImmediateClassType(resolve, substitutor);
  }

  @Override
  public @NotNull PsiClassType createType(@NotNull PsiClass resolve, @NotNull PsiSubstitutor substitutor, @Nullable LanguageLevel languageLevel) {
    return new PsiImmediateClassType(resolve, substitutor, languageLevel);
  }

  @Override
  public @NotNull PsiClass createClass(@NotNull String name) throws IncorrectOperationException {
    return createClassInner("class", name);
  }

  @Override
  public @NotNull PsiClass createInterface(@NotNull String name) throws IncorrectOperationException {
    return createClassInner("interface", name);
  }

  @Override
  public @NotNull PsiClass createEnum(@NotNull String name) throws IncorrectOperationException {
    return createClassInner("enum", name);
  }

  @Override
  public @NotNull PsiClass createRecord(@NotNull String name) throws IncorrectOperationException {
    return createClassInner("record", name);
  }

  @Override
  public @NotNull PsiClass createAnnotationType(@NotNull String name) throws IncorrectOperationException {
    return createClassInner("@interface", name);
  }

  private PsiClass createClassInner(String type, String name) {
    PsiUtil.checkIsIdentifier(myManager, name);
    PsiJavaFile aFile = createDummyJavaFile("public " + type +  " " +  name + ("record".equals(type) ? "()" : "") + " { }");
    PsiClass[] classes = aFile.getClasses();
    if (classes.length != 1) {
      throw new IncorrectOperationException("Incorrect " + type + " name \"" + name + "\".");
    }
    return classes[0];
  }

  @Override
  public @NotNull PsiTypeElement createTypeElement(@NotNull PsiType psiType) {
    LightTypeElement element = new LightTypeElement(myManager, psiType);
    CodeEditUtil.setNodeGenerated(element.getNode(), true);
    return element;
  }

  @Override
  public @NotNull PsiJavaCodeReferenceElement createReferenceElementByType(@NotNull PsiClassType type) {
    return type instanceof PsiClassReferenceType
           ? ((PsiClassReferenceType)type).getReference()
           : new LightClassTypeReference(myManager, type);
  }

  @Override
  public @NotNull PsiTypeParameterList createTypeParameterList() {
    PsiTypeParameterList parameterList = createMethodFromText("void foo()", null).getTypeParameterList();
    assert parameterList != null;
    return parameterList;
  }

  @Override
  public @NotNull PsiTypeParameter createTypeParameter(@NotNull String name, PsiClassType @NotNull [] superTypes) {
    StringBuilder builder = new StringBuilder();
    builder.append("public <").append(name);
    if (superTypes.length > 1 || superTypes.length == 1 && !superTypes[0].equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
      builder.append(" extends ");
      for (PsiClassType type : superTypes) {
        if (!type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
          builder.append(type.getCanonicalText(true)).append('&');
        }
      }
      builder.delete(builder.length() - 1, builder.length());
    }
    builder.append("> void foo(){}");
    try {
      return createMethodFromText(builder.toString(), null).getTypeParameters()[0];
    }
    catch (RuntimeException e) {
      throw new IncorrectOperationException("type parameter text: " + builder, (Throwable)e);
    }
  }

  @Override
  public @NotNull PsiField createField(@NotNull String name, @NotNull PsiType type) throws IncorrectOperationException {
    PsiUtil.checkIsIdentifier(myManager, name);
    if (PsiTypes.nullType().equals(type)) {
      throw new IncorrectOperationException("Cannot create field with type \"null\".");
    }

    String text = "class _Dummy_ { private " + GenericsUtil.getVariableTypeByExpressionType(type).getCanonicalText(true) + " " + name + "; }";
    PsiJavaFile aFile = createDummyJavaFile(text);
    PsiClass[] classes = aFile.getClasses();
    if (classes.length < 1) {
      throw new IncorrectOperationException("Class was not created " + text);
    }
    PsiClass psiClass = classes[0];
    PsiField[] fields = psiClass.getFields();
    if (fields.length < 1) {
      throw new IncorrectOperationException("Field was not created " + text);
    }
    PsiField field = fields[0];
    field = (PsiField)JavaCodeStyleManager.getInstance(myManager.getProject()).shortenClassReferences(field);
    return (PsiField)CodeStyleManager.getInstance(myManager.getProject()).reformat(field);
  }

  @Override
  public @NotNull PsiMethod createMethod(@NotNull String name, PsiType returnType) throws IncorrectOperationException {
    PsiUtil.checkIsIdentifier(myManager, name);
    if (PsiTypes.nullType().equals(returnType)) {
      throw new IncorrectOperationException("Cannot create method with type \"null\".");
    }

    String canonicalText = GenericsUtil.getVariableTypeByExpressionType(returnType).getCanonicalText(true);
    PsiJavaFile aFile = createDummyJavaFile("class _Dummy_ { public " + canonicalText + " " + name + "() {\n} }");
    PsiClass[] classes = aFile.getClasses();
    if (classes.length < 1) {
      throw new IncorrectOperationException("Class was not created. Method name: " + name + "; return type: " + canonicalText);
    }
    PsiMethod[] methods = classes[0].getMethods();
    if (methods.length < 1) {
      throw new IncorrectOperationException("Method was not created. Method name: " + name + "; return type: " + canonicalText);
    }
    PsiMethod method = methods[0];
    method = (PsiMethod)JavaCodeStyleManager.getInstance(myManager.getProject()).shortenClassReferences(method);
    return (PsiMethod)CodeStyleManager.getInstance(myManager.getProject()).reformat(method);
  }

  @Override
  public @NotNull PsiMethod createMethod(@NotNull String name, PsiType returnType, PsiElement context) throws IncorrectOperationException {
    return createMethodFromText("public " + GenericsUtil.getVariableTypeByExpressionType(returnType).getCanonicalText(true) + " " + name + "() {}", context);
  }

  @Override
  public @NotNull PsiMethod createConstructor() {
    return createConstructor("_Dummy_");
  }

  @Override
  public @NotNull PsiMethod createConstructor(@NotNull String name) {
    PsiJavaFile aFile = createDummyJavaFile("class " + name + " { public " + name + "() {} }");
    PsiMethod method = aFile.getClasses()[0].getMethods()[0];
    return (PsiMethod)CodeStyleManager.getInstance(myManager.getProject()).reformat(method);
  }

  @Override
  public @NotNull PsiMethod createConstructor(@NotNull String name, PsiElement context) {
    return createMethodFromText(name + "() {}", context);
  }

  @Override
  public @NotNull PsiClassInitializer createClassInitializer() throws IncorrectOperationException {
    PsiJavaFile aFile = createDummyJavaFile("class _Dummy_ { {} }");
    PsiClassInitializer classInitializer = aFile.getClasses()[0].getInitializers()[0];
    return (PsiClassInitializer)CodeStyleManager.getInstance(myManager.getProject()).reformat(classInitializer);
  }

  @Override
  public @NotNull PsiParameter createParameter(@NotNull String name, @NotNull PsiType type) throws IncorrectOperationException {
    PsiUtil.checkIsIdentifier(myManager, name);
    if (PsiTypes.nullType().equals(type)) {
      throw new IncorrectOperationException("Cannot create parameter with type \"null\".");
    }

    String text = type.getCanonicalText(true) + " " + name;
    PsiParameter parameter = createParameterFromText(text, null);
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myManager.getProject());
    PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL,
                                JavaCodeStyleSettingsFacade.getInstance(myManager.getProject()).isGenerateFinalParameters());
    GeneratedMarkerVisitor.markGenerated(parameter);
    parameter = (PsiParameter)JavaCodeStyleManager.getInstance(myManager.getProject()).shortenClassReferences(parameter);
    return (PsiParameter)codeStyleManager.reformat(parameter);
  }

  @Override
  public PsiParameter createParameter(@NotNull String name, @NotNull PsiType type, PsiElement context) throws IncorrectOperationException {
    String text = "void f(" + type.getCanonicalText(true) + " " + name + ") {}";
    PsiMethod psiMethod = createMethodFromText(text, context);
    PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
    if (parameters.length != 1) {
      throw new IncorrectOperationException("Incorrect method was created: " + psiMethod.getText());
    }
    return parameters[0];
  }

  @Override
  public @NotNull PsiCodeBlock createCodeBlock() {
    PsiCodeBlock block = createCodeBlockFromText("{}", null);
    return (PsiCodeBlock)CodeStyleManager.getInstance(myManager.getProject()).reformat(block);
  }

  @Override
  public @NotNull PsiClassType createType(@NotNull PsiClass aClass) {
    return new PsiImmediateClassType(aClass, aClass instanceof PsiTypeParameter ? PsiSubstitutor.EMPTY : createRawSubstitutor(aClass));
  }

  @Override
  public @NotNull PsiClassType createType(@NotNull PsiJavaCodeReferenceElement classReference) {
    return new PsiClassReferenceType(classReference, null);
  }

  @Override
  public @NotNull PsiClassType createType(@NotNull PsiClass aClass, PsiType parameter) {
    PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
    assert typeParameters.length == 1 : aClass;

    return createType(aClass, PsiSubstitutor.EMPTY.put(typeParameters[0], parameter));
  }

  @Override
  public @NotNull PsiClassType createType(@NotNull PsiClass aClass, PsiType... parameters) {
    return createType(aClass, PsiSubstitutor.EMPTY.putAll(aClass, parameters));
  }

  @Override
  public @NotNull PsiSubstitutor createRawSubstitutor(@NotNull PsiTypeParameterListOwner owner) {
    Map<PsiTypeParameter, PsiType> substitutorMap = null;
    for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(owner)) {
      if (substitutorMap == null) substitutorMap = new HashMap<>();
      substitutorMap.put(parameter, null);
    }
    return PsiSubstitutor.createSubstitutor(substitutorMap);
  }

  @Override
  public @NotNull PsiSubstitutor createRawSubstitutor(@NotNull PsiSubstitutor baseSubstitutor, PsiTypeParameter @NotNull [] typeParameters) {
    Map<PsiTypeParameter, PsiType> substitutorMap = null;
    for (PsiTypeParameter parameter : typeParameters) {
      if (substitutorMap == null) substitutorMap = new HashMap<>();
      substitutorMap.put(parameter, null);
    }
    return PsiSubstitutor.createSubstitutor(substitutorMap).putAll(baseSubstitutor);
  }

  @Override
  public @NotNull PsiElement createDummyHolder(@NotNull String text, @NotNull IElementType type, @Nullable PsiElement context) {
    DummyHolder result = DummyHolderFactory.createHolder(myManager, context);
    FileElement holder = result.getTreeElement();
    Language language = type.getLanguage();
    ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
    assert parserDefinition != null : "No parser definition for language " + language;
    Project project = myManager.getProject();
    Lexer lexer = parserDefinition.createLexer(project);
    PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(project, holder, lexer, language, text);
    ASTNode node = parserDefinition.createParser(project).parse(type, builder);
    holder.rawAddChildren((TreeElement)node);
    PsiElement psi = node.getPsi();
    assert psi != null : text;
    return psi;
  }

  @Override
  public @NotNull PsiSubstitutor createSubstitutor(@NotNull Map<PsiTypeParameter, PsiType> map) {
    return PsiSubstitutor.createSubstitutor(map);
  }

  @Override
  public @Nullable PsiPrimitiveType createPrimitiveType(@NotNull String text) {
    return PsiJavaParserFacadeImpl.getPrimitiveType(text);
  }

  @Override
  public @NotNull PsiClassType createTypeByFQClassName(@NotNull String qName) {
    return createTypeByFQClassName(qName, GlobalSearchScope.allScope(myManager.getProject()));
  }

  @Override
  public @NotNull PsiClassType createTypeByFQClassName(@NotNull String qName, @NotNull GlobalSearchScope resolveScope) {
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

  @Override
  public @NotNull PsiJavaCodeReferenceElement createClassReferenceElement(@NotNull PsiClass aClass) {
    String text;
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

  @Override
  public @NotNull PsiJavaCodeReferenceElement createReferenceElementByFQClassName(@NotNull String qName, @NotNull GlobalSearchScope resolveScope) {
    String shortName = PsiNameHelper.getShortClassName(qName);
    return new LightClassReference(myManager, shortName, qName, resolveScope);
  }

  @Override
  public @NotNull PsiJavaCodeReferenceElement createFQClassNameReferenceElement(@NotNull String qName, @NotNull GlobalSearchScope resolveScope) {
    return new LightClassReference(myManager, qName, qName, resolveScope);
  }

  @Override
  public @NotNull PsiJavaCodeReferenceElement createPackageReferenceElement(@NotNull PsiPackage aPackage) throws IncorrectOperationException {
    if (aPackage.getQualifiedName().isEmpty()) {
      throw new IncorrectOperationException("Cannot create reference to default package.");
    }
    return new LightPackageReference(myManager, aPackage);
  }

  @Override
  public @NotNull PsiPackageStatement createPackageStatement(@NotNull String name) throws IncorrectOperationException {
    PsiJavaFile aFile = createDummyJavaFile("package " + name + ";");
    PsiPackageStatement stmt = aFile.getPackageStatement();
    if (stmt == null) {
      throw new IncorrectOperationException("Incorrect package name: " + name);
    }
    return stmt;
  }

  @Override
  public @NotNull PsiImportStaticStatement createImportStaticStatement(@NotNull PsiClass aClass, @NotNull String memberName) throws IncorrectOperationException {
    if (aClass instanceof PsiAnonymousClass) {
      throw new IncorrectOperationException("Cannot create import statement for anonymous class.");
    }
    else if (aClass.getParent() instanceof PsiDeclarationStatement) {
      throw new IncorrectOperationException("Cannot create import statement for local class.");
    }

    PsiJavaFile aFile = createDummyJavaFile("import static " + aClass.getQualifiedName() + "." + memberName + ";");
    PsiImportStatementBase statement = extractImport(aFile, true);
    return (PsiImportStaticStatement)CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
  }

  @Override
  public @NotNull PsiImportStaticStatement createImportStaticStatementFromText(@NotNull String classFullyQualifiedName, @NotNull String memberName) throws IncorrectOperationException {
    PsiJavaFile aFile = createDummyJavaFile("import static " + classFullyQualifiedName + "." + memberName + ";");
    PsiImportStatementBase statement = extractImport(aFile, true);
    return (PsiImportStaticStatement)CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
  }

  @Override
  public @NotNull PsiImportModuleStatement createImportModuleStatementFromText(@NotNull String moduleName)
    throws IncorrectOperationException {
    PsiJavaFile aFile = createDummyJavaFile("import module " + moduleName + ";");
    PsiImportList importList = aFile.getImportList();
    if (importList == null) throw new IncorrectOperationException("Can't create module with name: " + moduleName);
    PsiImportModuleStatement[] statements = importList.getImportModuleStatements();
    if (statements.length != 1) throw new IncorrectOperationException("Created more than one module with name: " + moduleName);
    PsiImportModuleStatement statement = statements[0];
    GeneratedMarkerVisitor.markGenerated(statement); //Don't reformat because there is a chance of infinite recursion
    return statement;
  }

  @Override
  public @NotNull PsiParameterList createParameterList(String @NotNull [] names, PsiType @NotNull [] types) throws IncorrectOperationException {
    StringBuilder builder = new StringBuilder();
    builder.append("void method(");
    for (int i = 0; i < names.length; i++) {
      if (i > 0) builder.append(", ");
      builder.append(types[i].getCanonicalText(true)).append(' ').append(names[i]);
    }
    builder.append(");");
    return createMethodFromText(builder.toString(), null).getParameterList();
  }

  @Override
  public @NotNull PsiReferenceList createReferenceList(PsiJavaCodeReferenceElement @NotNull [] references) throws IncorrectOperationException {
    StringBuilder builder = new StringBuilder();
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

  @Override
  public @NotNull PsiJavaCodeReferenceElement createPackageReferenceElement(@NotNull String packageName) throws IncorrectOperationException {
    if (packageName.isEmpty()) {
      throw new IncorrectOperationException("Cannot create reference to default package.");
    }
    return new LightPackageReference(myManager, packageName);
  }

  @Override
  public @NotNull PsiReferenceExpression createReferenceExpression(@NotNull PsiClass aClass) throws IncorrectOperationException {
    String text;
    if (aClass instanceof PsiImplicitClass) {
      throw new IncorrectOperationException("Cannot create reference to implicitly declared class");
    }
    if (aClass instanceof PsiAnonymousClass) {
      text = ((PsiAnonymousClass)aClass).getBaseClassType().getPresentableText();
    }
    else {
      text = aClass.getName();
    }
    return new LightClassReferenceExpression(myManager, text, aClass);
  }

  @Override
  public @NotNull PsiReferenceExpression createReferenceExpression(@NotNull PsiPackage aPackage) throws IncorrectOperationException {
    if (aPackage.getQualifiedName().isEmpty()) {
      throw new IncorrectOperationException("Cannot create reference to default package.");
    }
    return new LightPackageReferenceExpression(myManager, aPackage);
  }

  @Override
  public @NotNull PsiIdentifier createIdentifier(@NotNull String text) throws IncorrectOperationException {
    PsiUtil.checkIsIdentifier(myManager, text);
    return new LightIdentifier(myManager, text);
  }

  @Override
  public @NotNull PsiKeyword createKeyword(@NotNull String text) throws IncorrectOperationException {
    if (!PsiNameHelper.getInstance(myManager.getProject()).isKeyword(text)) {
      throw new IncorrectOperationException("\"" + text + "\" is not a keyword.");
    }
    return new LightKeyword(myManager, text);
  }

  @Override
  public @NotNull PsiKeyword createKeyword(@NotNull String keyword, PsiElement context) throws IncorrectOperationException {
    LanguageLevel level = PsiUtil.getLanguageLevel(context);
    if (!PsiUtil.isKeyword(keyword, level) && !PsiUtil.isSoftKeyword(keyword, level)) {
      throw new IncorrectOperationException("\"" + keyword + "\" is not a keyword.");
    }
    return new LightKeyword(myManager, keyword);
  }

  @Override
  public @NotNull PsiImportStatement createImportStatement(@NotNull PsiClass aClass) throws IncorrectOperationException {
    if (aClass instanceof PsiAnonymousClass) {
      throw new IncorrectOperationException("Cannot create import statement for anonymous class.");
    }
    else if (aClass.getParent() instanceof PsiDeclarationStatement) {
      throw new IncorrectOperationException("Cannot create import statement for local class.");
    }

    PsiJavaFile aFile = createDummyJavaFile("import " + aClass.getQualifiedName() + ";");
    PsiImportStatementBase statement = extractImport(aFile, false);
    GeneratedMarkerVisitor.markGenerated(statement);  //Don't reformat because there is a chance of infinite recursion
    return (PsiImportStatement)statement;
  }

  @Override
  public @NotNull PsiImportStatement createImportStatementOnDemand(@NotNull String packageName) throws IncorrectOperationException {
    if (packageName.isEmpty()) {
      throw new IncorrectOperationException("Cannot create import statement for default package.");
    }
    if (!PsiNameHelper.getInstance(myManager.getProject()).isQualifiedName(packageName)) {
      throw new IncorrectOperationException("Incorrect package name: \"" + packageName + "\".");
    }

    PsiJavaFile aFile = createDummyJavaFile("import " + packageName + ".*;");
    PsiImportStatementBase statement = extractImport(aFile, false);
    GeneratedMarkerVisitor.markGenerated(statement); //Don't reformat because there is a chance of infinite recursion
    return (PsiImportStatement)statement;
  }

  @Override
  public @NotNull PsiDeclarationStatement createVariableDeclarationStatement(@NotNull String name,
                                                                             @NotNull PsiType type,
                                                                             @Nullable PsiExpression initializer) throws IncorrectOperationException {
    return createVariableDeclarationStatement(name, type, initializer, null);
  }

  @Override
  public @NotNull PsiDeclarationStatement createVariableDeclarationStatement(@NotNull String name,
                                                                             @NotNull PsiType type,
                                                                             @Nullable PsiExpression initializer,
                                                                             @Nullable PsiElement context) throws IncorrectOperationException {
    if (!isIdentifier(name)) {
      throw new IncorrectOperationException("\"" + name + "\" is not an identifier.");
    }
    if (PsiTypes.nullType().equals(type)) {
      throw new IncorrectOperationException("Cannot create variable with type \"null\".");
    }

    String text = "X " + name + (initializer != null ? " = x" : "") + ";";
    PsiDeclarationStatement statement = (PsiDeclarationStatement)createStatementFromText(text, context);

    PsiVariable variable = (PsiVariable)statement.getDeclaredElements()[0];
    replace(variable.getTypeElement(), createTypeElement(GenericsUtil.getVariableTypeByExpressionType(type)), text);

    boolean generateFinalLocals =
      context != null && JavaFileCodeStyleFacade.forContext(context.getContainingFile()).isGenerateFinalLocals();
    PsiUtil.setModifierProperty(variable, PsiModifier.FINAL, generateFinalLocals);

    if (initializer != null) {
      replace(variable.getInitializer(), initializer, text);
    }

    GeneratedMarkerVisitor.markGenerated(statement);
    return statement;
  }

  @Override
  public PsiResourceVariable createResourceVariable(@NotNull String name,
                                                    @NotNull PsiType type,
                                                    @Nullable PsiExpression initializer,
                                                    @Nullable PsiElement context) {
    PsiTryStatement tryStatement = (PsiTryStatement)createStatementFromText("try (X x = null){}", context);
    PsiResourceList resourceList = tryStatement.getResourceList();
    assert resourceList != null;
    PsiResourceVariable resourceVariable = (PsiResourceVariable)resourceList.iterator().next();
    resourceVariable.getTypeElement().replace(createTypeElement(type));
    PsiIdentifier nameIdentifier = resourceVariable.getNameIdentifier();
    assert nameIdentifier != null;
    nameIdentifier.replace(createIdentifier(name));
    if (initializer != null) {
      resourceVariable.setInitializer(initializer);
    }
    return resourceVariable;
  }

  private static void replace(@Nullable PsiElement original, @NotNull PsiElement replacement, @NotNull String message) {
    assert original != null : message;
    original.replace(replacement);
  }

  @Override
  public @NotNull PsiDocTag createParamTag(@NotNull String parameterName, String description) throws IncorrectOperationException {
    StringBuilder builder = new StringBuilder();
    builder.append(" * @param ");
    builder.append(parameterName);
    builder.append(" ");
    String[] strings = description.split("\\n");
    for (int i = 0; i < strings.length; i++) {
      if (i > 0) builder.append("\n * ");
      builder.append(strings[i]);
    }
    return createDocTagFromText(builder.toString());
  }

  @Override
  public @NotNull PsiAnnotation createAnnotationFromText(@NotNull String annotationText, @Nullable PsiElement context) throws IncorrectOperationException {
    PsiAnnotation psiAnnotation = super.createAnnotationFromText(annotationText, context);
    GeneratedMarkerVisitor.markGenerated(psiAnnotation);
    return psiAnnotation;
  }

  public PsiAnnotation createAnnotationFromText(@NotNull String annotationText,
                                                @Nullable PsiElement context,
                                                boolean markGenerated) throws IncorrectOperationException {
    PsiAnnotation psiAnnotation = super.createAnnotationFromText(annotationText, context);
    if (markGenerated) {
      GeneratedMarkerVisitor.markGenerated(psiAnnotation);
    }
    return psiAnnotation;
  }

  @Override
  public @NotNull PsiCodeBlock createCodeBlockFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    PsiCodeBlock psiCodeBlock = super.createCodeBlockFromText(text, context);
    GeneratedMarkerVisitor.markGenerated(psiCodeBlock);
    return psiCodeBlock;
  }

  @Override
  public @NotNull PsiEnumConstant createEnumConstantFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    PsiEnumConstant enumConstant = super.createEnumConstantFromText(text, context);
    GeneratedMarkerVisitor.markGenerated(enumConstant);
    return enumConstant;
  }

  @Override
  public @NotNull PsiExpression createExpressionFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    PsiExpression expression = super.createExpressionFromText(text, context);
    GeneratedMarkerVisitor.markGenerated(expression);
    return expression;
  }

  @Override
  public @NotNull PsiField createFieldFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    PsiField psiField = super.createFieldFromText(text, context);
    GeneratedMarkerVisitor.markGenerated(psiField);
    return psiField;
  }

  @Override
  public @NotNull PsiParameter createParameterFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    PsiParameter parameter = super.createParameterFromText(text, context);
    GeneratedMarkerVisitor.markGenerated(parameter);
    return parameter;
  }

  @Override
  public @NotNull PsiStatement createStatementFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    PsiStatement statement = super.createStatementFromText(text, context);
    GeneratedMarkerVisitor.markGenerated(statement);
    return statement;
  }

  @Override
  public @NotNull PsiType createTypeFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    return createTypeInner(text, context, true);
  }

  @Override
  public @NotNull PsiTypeParameter createTypeParameterFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException {
    PsiTypeParameter typeParameter = super.createTypeParameterFromText(text, context);
    GeneratedMarkerVisitor.markGenerated(typeParameter);
    return typeParameter;
  }

  @Override
  public @NotNull PsiMethod createMethodFromText(@NotNull String text,
                                                 PsiElement context,
                                                 LanguageLevel level) throws IncorrectOperationException {
    PsiMethod method = super.createMethodFromText(text, context, level);
    GeneratedMarkerVisitor.markGenerated(method);
    return method;
  }

  private static PsiImportStatementBase extractImport(PsiJavaFile aFile, boolean isStatic) {
    PsiImportList importList = aFile.getImportList();
    assert importList != null : aFile;
    PsiImportStatementBase[] statements = isStatic ? importList.getImportStaticStatements() : importList.getImportStatements();
    assert statements.length == 1 : aFile.getText();
    return statements[0];
  }

  private static final JavaParserUtil.ParserWrapper CATCH_SECTION = builder -> JavaParser.INSTANCE.getStatementParser().parseCatchBlock(builder);

  @Override
  public @NotNull PsiCatchSection createCatchSection(@NotNull PsiType exceptionType,
                                                     @NotNull String exceptionName,
                                                     @Nullable PsiElement context) throws IncorrectOperationException {
    if (!(exceptionType instanceof PsiClassType || exceptionType instanceof PsiDisjunctionType)) {
      throw new IncorrectOperationException("Unexpected type:" + exceptionType);
    }

    String text = "catch (" + exceptionType.getCanonicalText(true) +  " " + exceptionName + ") {}";
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, CATCH_SECTION, level(context)), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiCatchSection)) {
      throw new IncorrectOperationException("Incorrect catch section '" + text + "'. Parsed element: " + element);
    }

    Project project = myManager.getProject();
    JavaPsiImplementationHelper helper = JavaPsiImplementationHelper.getInstance(project);
    helper.setupCatchBlock(exceptionName, exceptionType, context, (PsiCatchSection)element);
    CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
    PsiCatchSection catchSection = (PsiCatchSection)styleManager.reformat(element);

    GeneratedMarkerVisitor.markGenerated(catchSection);
    return catchSection;
  }

  @Override
  public @NotNull PsiFragment createStringTemplateFragment(@NotNull String newText, @NotNull IElementType tokenType, @Nullable PsiElement context) {
    int index;
    if (tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN) {
      newText += "}\"\"\"";
      index = 0;
    }
    else if (tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_MID) {
      newText = "\"\"\"\n\\{" + newText + "}\"\"\"";
      index = 1;
    }
    else if (tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_END) {
      newText = "\"\"\"\n\\{" + newText;
      index = 1;
    }
    else if (tokenType == JavaTokenType.STRING_TEMPLATE_BEGIN) {
      newText += "}\"";
      index = 0;
    }
    else if (tokenType == JavaTokenType.STRING_TEMPLATE_MID) {
      newText = "\"\\{" + newText + "}\"";
      index = 1;
    }
    else if (tokenType == JavaTokenType.STRING_TEMPLATE_END) {
      newText = "\"\\{" + newText;
      index = 1;
    }
    else {
      throw new IllegalArgumentException();
    }
    PsiTemplateExpression expression = (PsiTemplateExpression)createExpressionFromText(newText, context);
    PsiTemplate template = expression.getTemplate();
    assert template != null;
    PsiFragment fragment = template.getFragments().get(index);
    GeneratedMarkerVisitor.markGenerated(fragment);
    return fragment;
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

  @Override
  public void dispose() {

  }
}