/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.impl;

import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.light.*;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

import static com.intellij.openapi.util.text.StringUtil.join;

public class PsiElementFactoryImpl extends PsiJavaParserFacadeImpl implements PsiElementFactory {
  private PsiClass myArrayClass;
  private PsiClass myArrayClass15;
  private PsiJavaFile myDummyJavaFile;

  public PsiElementFactoryImpl(final PsiManagerEx manager) {
    super(manager);
  }

  public PsiJavaFile getDummyJavaFile() {
    if (myDummyJavaFile == null) {
      myDummyJavaFile = createDummyJavaFile("");
    }

    return myDummyJavaFile;
  }

  @NotNull
  @Override
  public PsiClass getArrayClass(@NotNull final LanguageLevel languageLevel) {
    if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_5)) {
      if (myArrayClass == null) {
        final String body = "public class __Array__{\n public final int length;\n public Object clone() {}\n}";
        myArrayClass = createClassFromText(body, null).getInnerClasses()[0];
      }
      return myArrayClass;
    }
    else {
      if (myArrayClass15 == null) {
        final String body = "public class __Array__<T>{\n public final int length;\n public T[] clone() {}\n}";
        myArrayClass15 = createClassFromText(body, null).getInnerClasses()[0];
      }
      return myArrayClass15;
    }
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
  public PsiClassType createType(@NotNull final PsiClass resolve, @NotNull final PsiSubstitutor substitutor) {
    return new PsiImmediateClassType(resolve, substitutor);
  }

  @NotNull
  @Override
  public PsiClassType createType(@NotNull final PsiClass resolve,
                                 @NotNull final PsiSubstitutor substitutor,
                                 @NotNull final LanguageLevel languageLevel) {
    return new PsiImmediateClassType(resolve, substitutor, languageLevel);
  }

  @NotNull
  @Override
  public PsiClassType createType(@NotNull final PsiClass resolve,
                                 @NotNull final PsiSubstitutor substitutor,
                                 @NotNull final LanguageLevel languageLevel,
                                 @NotNull final PsiAnnotation[] annotations) {
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

  private PsiClass createClassInner(final String type, final String name) {
    PsiUtil.checkIsIdentifier(myManager, name);
    final PsiJavaFile aFile = createDummyJavaFile(join("public ", type, " ", name, " { }"));
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

    final PsiClassType.ClassResolveResult resolveResult = type.resolveGenerics();
    final PsiClass refClass = resolveResult.getElement();
    assert refClass != null : type;
    return new LightClassReference(myManager, type.getPresentableText(), refClass, resolveResult.getSubstitutor());
  }

  @NotNull
  @Override
  public PsiField createField(@NotNull final String name, @NotNull final PsiType type) throws IncorrectOperationException {
    PsiUtil.checkIsIdentifier(myManager, name);
    if (PsiType.NULL.equals(type)) {
      throw new IncorrectOperationException("Cannot create field with type \"null\".");
    }

    final PsiJavaFile aFile = createDummyJavaFile(join("class _Dummy_ { private ", type.getCanonicalText(), " ", name, "; }"));
    final PsiClass psiClass = aFile.getClasses()[0];
    final PsiField field = psiClass.getFields()[0];
    JavaCodeStyleManager.getInstance(myManager.getProject()).shortenClassReferences(field);
    return (PsiField)CodeStyleManager.getInstance(myManager.getProject()).reformat(field);
  }

  @NotNull
  @Override
  public PsiMethod createMethod(@NotNull final String name, final PsiType returnType) throws IncorrectOperationException {
    PsiUtil.checkIsIdentifier(myManager, name);
    if (PsiType.NULL.equals(returnType)) {
      throw new IncorrectOperationException("Cannot create method with type \"null\".");
    }

    final PsiJavaFile aFile = createDummyJavaFile(join("class _Dummy_ { public " + returnType.getCanonicalText(), " ", name, "() {} }"));
    final PsiMethod method = aFile.getClasses()[0].getMethods()[0];
    JavaCodeStyleManager.getInstance(myManager.getProject()).shortenClassReferences(method);
    return (PsiMethod)CodeStyleManager.getInstance(myManager.getProject()).reformat(method);
  }

  @NotNull
  @Override
  public PsiMethod createConstructor() {
    final PsiJavaFile aFile = createDummyJavaFile("class _Dummy_ { public _Dummy_() {} }");
    final PsiMethod method = aFile.getClasses()[0].getMethods()[0];
    return (PsiMethod)CodeStyleManager.getInstance(myManager.getProject()).reformat(method);
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

    final String text = join(type.getCanonicalText() + " " + name);
    final PsiParameter parameter = createParameterFromText(text, null);
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myManager.getProject());
    PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL,
                                CodeStyleSettingsManager.getSettings(myManager.getProject()).GENERATE_FINAL_PARAMETERS);
    markGenerated(parameter);
    JavaCodeStyleManager.getInstance(myManager.getProject()).shortenClassReferences(parameter);
    return (PsiParameter)codeStyleManager.reformat(parameter);
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

    final Map<PsiTypeParameter, PsiType> map = Collections.singletonMap(typeParameters[0], parameter);
    return createType(aClass, createSubstitutor(map));
  }

  @NotNull
  @Override
  public PsiClassType createType(@NotNull final PsiClass aClass, final PsiType... parameters) {
    final PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
    assert parameters.length == typeParameters.length;

    final Map<PsiTypeParameter, PsiType> map = new java.util.HashMap<PsiTypeParameter, PsiType>();
    for (int i = 0; i < parameters.length; i++) {
      map.put(typeParameters[i], parameters[i]);
    }
    return createType(aClass, createSubstitutor(map));
  }

  @NotNull
  @Override
  public PsiType detachType(@NotNull final PsiType type) {
    return type;
  }

  @NotNull
  @Override
  public PsiSubstitutor createRawSubstitutor(@NotNull final PsiTypeParameterListOwner owner) {
    Map<PsiTypeParameter, PsiType> substitutorMap = null;
    for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(owner)) {
      if (substitutorMap == null) substitutorMap = new HashMap<PsiTypeParameter, PsiType>();
      substitutorMap.put(parameter, null);
    }
    return PsiSubstitutorImpl.createSubstitutor(substitutorMap);
  }

  @NotNull
  @Override
  public PsiSubstitutor createRawSubstitutor(@NotNull final PsiSubstitutor baseSubstitutor, @NotNull final PsiTypeParameter[] typeParameters) {
    Map<PsiTypeParameter, PsiType> substitutorMap = null;
    for (PsiTypeParameter parameter : typeParameters) {
      if (substitutorMap == null) substitutorMap = new HashMap<PsiTypeParameter, PsiType>();
      substitutorMap.put(parameter, null);
    }
    return baseSubstitutor.putAll(PsiSubstitutorImpl.createSubstitutor(substitutorMap));
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
    if (aPackage.getQualifiedName().length() == 0) {
      throw new IncorrectOperationException("Cannot create reference to default package.");
    }
    return new LightPackageReference(myManager, aPackage);
  }

  @NotNull
  @Override
  public PsiPackageStatement createPackageStatement(@NotNull final String name) throws IncorrectOperationException {
    final PsiJavaFile aFile = createDummyJavaFile(join("package ", name, ";"));
    final PsiPackageStatement stmt = aFile.getPackageStatement();
    if (stmt == null) {
      throw new IncorrectOperationException("Incorrect package name: " + name);
    }
    return stmt;
  }

  @NotNull
  @Override
  public PsiJavaCodeReferenceCodeFragment createReferenceCodeFragment(@NotNull final String text,
                                                                      final PsiElement context,
                                                                      final boolean isPhysical,
                                                                      final boolean isClassesAccepted) {
    final PsiJavaCodeReferenceCodeFragmentImpl result =
      new PsiJavaCodeReferenceCodeFragmentImpl(myManager.getProject(), isPhysical, "fragment.java", text, isClassesAccepted);
    result.setContext(context);
    return result;
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

    final PsiJavaFile aFile = createDummyJavaFile(join("import static ", aClass.getQualifiedName(), ".", memberName, ";"));
    final PsiImportStatementBase statement = extractImport(aFile, true);
    return (PsiImportStaticStatement)CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
  }

  @NotNull
  @Override
  public PsiParameterList createParameterList(@NotNull final String[] names, @NotNull final PsiType[] types) throws IncorrectOperationException {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("void method(");
      for (int i = 0; i < names.length; i++) {
        if (i > 0) builder.append(", ");
        builder.append(types[i].getCanonicalText()).append(' ').append(names[i]);
      }
      builder.append(");");
      return createMethodFromText(builder.toString(), null).getParameterList();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @NotNull
  @Override
  public PsiReferenceList createReferenceList(@NotNull final PsiJavaCodeReferenceElement[] references) throws IncorrectOperationException {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
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
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @NotNull
  @Override
  public PsiJavaCodeReferenceElement createPackageReferenceElement(@NotNull final String packageName) throws IncorrectOperationException {
    if (packageName.length() == 0) {
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
    if (aPackage.getQualifiedName().length() == 0) {
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
    if (!JavaPsiFacade.getInstance(myManager.getProject()).getNameHelper().isKeyword(text)) {
      throw new IncorrectOperationException("\"" + text + "\" is not a keyword.");
    }
    return new LightKeyword(myManager, text);
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

    final PsiJavaFile aFile = createDummyJavaFile(join("import ", aClass.getQualifiedName(), ";"));
    final PsiImportStatementBase statement = extractImport(aFile, false);
    return (PsiImportStatement)CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
  }

  @NotNull
  @Override
  public PsiImportStatement createImportStatementOnDemand(@NotNull final String packageName) throws IncorrectOperationException {
    if (packageName.length() == 0) {
      throw new IncorrectOperationException("Cannot create import statement for default package.");
    }
    if (!JavaPsiFacade.getInstance(myManager.getProject()).getNameHelper().isQualifiedName(packageName)) {
      throw new IncorrectOperationException("Incorrect package name: \"" + packageName + "\".");
    }

    final PsiJavaFile aFile = createDummyJavaFile(join("import ", packageName, ".*;"));
    final PsiImportStatementBase statement = extractImport(aFile, false);
    return (PsiImportStatement)CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
  }

  @NotNull
  @Override
  public PsiDeclarationStatement createVariableDeclarationStatement(@NotNull final String name,
                                                                    @NotNull final PsiType type,
                                                                    final PsiExpression initializer) throws IncorrectOperationException {
    if (!JavaPsiFacade.getInstance(myManager.getProject()).getNameHelper().isIdentifier(name)) {
      throw new IncorrectOperationException("\"" + name + "\" is not an identifier.");
    }
    if (PsiType.NULL.equals(type)) {
      throw new IncorrectOperationException("Cannot create variable with type \"null\".");
    }

    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    builder.append("X ").append(name);
    if (initializer != null) {
      builder.append(" = x");
    }
    builder.append(';');
    final String text = builder.toString();
    StringBuilderSpinAllocator.dispose(builder);

    final PsiDeclarationStatement statement = (PsiDeclarationStatement)createStatementFromText(text, null);
    final PsiVariable variable = (PsiVariable)statement.getDeclaredElements()[0];
    replace(variable.getTypeElement(), createTypeElement(type), text);
    PsiUtil.setModifierProperty(variable, PsiModifier.FINAL,
                                CodeStyleSettingsManager.getSettings(myManager.getProject()).GENERATE_FINAL_LOCALS);
    if (initializer != null) {
      replace(variable.getInitializer(), initializer, text);
    }
    markGenerated(statement);
    return statement;
  }

  @NotNull
  @Override
  public PsiDocTag createParamTag(@NotNull final String parameterName, @NonNls final String description) throws IncorrectOperationException {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
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
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @NotNull
  @Override
  public PsiExpressionCodeFragment createExpressionCodeFragment(@NotNull final String text,
                                                                final PsiElement context,
                                                                final PsiType expectedType,
                                                                final boolean isPhysical) {
    final PsiExpressionCodeFragmentImpl result =
      new PsiExpressionCodeFragmentImpl(myManager.getProject(), isPhysical, "fragment.java", text, expectedType);
    result.setContext(context);
    return result;
  }

  @NotNull
  @Override
  public JavaCodeFragment createCodeBlockCodeFragment(@NotNull final String text, @Nullable final PsiElement context, final boolean isPhysical) {
    final PsiCodeFragmentImpl result =
      new PsiCodeFragmentImpl(myManager.getProject(), JavaElementType.STATEMENTS, isPhysical, "fragment.java", text);
    result.setContext(context);
    return result;
  }

  @NotNull
  @Override
  public PsiTypeCodeFragment createTypeCodeFragment(@NotNull final String text, @Nullable final PsiElement context, final boolean isPhysical) {
    return createTypeCodeFragment(text, context, isPhysical, 0);
  }

  @NotNull
  @Override
  public PsiTypeCodeFragment createTypeCodeFragment(@NotNull final String text, @Nullable final PsiElement context, final boolean isPhysical, final int flags) {
    final PsiTypeCodeFragmentImpl result = new PsiTypeCodeFragmentImpl(myManager.getProject(), isPhysical, "fragment.java", text, flags);
    result.setContext(context);
    return result;
  }

  @NotNull
  @Override
  public PsiTypeCodeFragment createTypeCodeFragment(@NotNull final String text,
                                                    final PsiElement context,
                                                    final boolean isVoidValid,
                                                    final boolean isPhysical) {
    int flags = 0;
    if (isVoidValid) flags |= ALLOW_VOID;
    return createTypeCodeFragment(text, context, isPhysical, flags);
  }

  @NotNull
  @Override
  public PsiTypeCodeFragment createTypeCodeFragment(@NotNull final String text,
                                                    final PsiElement context,
                                                    final boolean isVoidValid,
                                                    final boolean isPhysical,
                                                    final boolean allowEllipsis) {
    int flags = 0;
    if (isVoidValid) flags |= ALLOW_VOID;
    if (allowEllipsis) flags |= ALLOW_ELLIPSIS;
    return createTypeCodeFragment(text, context, isPhysical, flags);
  }

  @NotNull
  @Override
  public PsiAnnotation createAnnotationFromText(@NotNull final String annotationText, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiAnnotation psiAnnotation = super.createAnnotationFromText(annotationText, context);
    markGenerated(psiAnnotation);
    return psiAnnotation;
  }

  @NotNull
  @Override
  public PsiCodeBlock createCodeBlockFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiCodeBlock psiCodeBlock = super.createCodeBlockFromText(text, context);
    markGenerated(psiCodeBlock);
    return psiCodeBlock;
  }

  @NotNull
  @Override
  public PsiEnumConstant createEnumConstantFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiEnumConstant enumConstant = super.createEnumConstantFromText(text, context);
    markGenerated(enumConstant);
    return enumConstant;
  }

  @NotNull
  @Override
  public PsiExpression createExpressionFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiExpression expression = super.createExpressionFromText(text, context);
    markGenerated(expression);
    return expression;
  }

  @NotNull
  @Override
  public PsiField createFieldFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiField psiField = super.createFieldFromText(text, context);
    markGenerated(psiField);
    return psiField;
  }

  @NotNull
  @Override
  public PsiParameter createParameterFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiParameter parameter = super.createParameterFromText(text, context);
    markGenerated(parameter);
    return parameter;
  }

  @NotNull
  @Override
  public PsiStatement createStatementFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiStatement statement = super.createStatementFromText(text, context);
    markGenerated(statement);
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
    markGenerated(typeParameter);
    return typeParameter;
  }

  @NotNull
  @Override
  public PsiElement createWhiteSpaceFromText(@NotNull @NonNls final String text) throws IncorrectOperationException {
    final PsiElement whitespace = super.createWhiteSpaceFromText(text);
    markGenerated(whitespace);
    return whitespace;
  }

  @NotNull
  @Override
  public PsiMethod createMethodFromText(@NotNull final String text,
                                        final PsiElement context,
                                        final LanguageLevel level) throws IncorrectOperationException {
    final PsiMethod method = super.createMethodFromText(text, context, level);
    markGenerated(method);
    return method;
  }

  @NotNull
  @Override
  public PsiCatchSection createCatchSection(@NotNull final PsiType exceptionType,
                                            @NotNull final String exceptionName,
                                            final PsiElement context) throws IncorrectOperationException {
    final PsiCatchSection psiCatchSection = super.createCatchSection(exceptionType, exceptionName, context);
    markGenerated(psiCatchSection);
    return psiCatchSection;
  }

  private static void markGenerated(final PsiElement element) {
    ((TreeElement)element.getNode()).acceptTree(new GeneratedMarkerVisitor());
  }

  private static PsiImportStatementBase extractImport(final PsiJavaFile aFile, final boolean isStatic) {
    final PsiImportList importList = aFile.getImportList();
    assert importList != null : aFile;
    final PsiImportStatementBase[] statements = isStatic ? importList.getImportStaticStatements() : importList.getImportStatements();
    assert statements.length == 1 : aFile.getText();
    return statements[0];
  }

  private static void replace(final PsiElement original, final PsiElement replacement, final String message) {
    assert original != null : message;
    original.replace(replacement);
  }

  /**
   * @deprecated use {@link PsiJavaParserFacadeImpl#getPrimitiveType(String)} (remove in IDEA 11).
   */
  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static PsiPrimitiveType getPrimitiveType(final String text) {
    return PsiJavaParserFacadeImpl.getPrimitiveType(text);
  }
}
