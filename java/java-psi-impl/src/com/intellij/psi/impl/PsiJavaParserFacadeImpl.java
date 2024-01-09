// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.parser.BasicReferenceParser;
import com.intellij.lang.java.parser.DeclarationParser;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class PsiJavaParserFacadeImpl implements PsiJavaParserFacade {
  private static final String DUMMY_FILE_NAME = "_Dummy_." + JavaFileType.INSTANCE.getDefaultExtension();

  private static final JavaParserUtil.ParserWrapper ANNOTATION =
    builder -> JavaParser.INSTANCE.getDeclarationParser().parseAnnotation(builder);

  private static final JavaParserUtil.ParserWrapper PARAMETER =
    builder -> JavaParser.INSTANCE.getDeclarationParser().parseParameter(builder, true, false, false);

  private static final JavaParserUtil.ParserWrapper RESOURCE = builder -> JavaParser.INSTANCE.getDeclarationParser().parseResource(builder);

  private static final JavaParserUtil.ParserWrapper TYPE = builder -> {
    int flags = BasicReferenceParser.EAT_LAST_DOT | BasicReferenceParser.ELLIPSIS | BasicReferenceParser.WILDCARD | BasicReferenceParser.DISJUNCTIONS | BasicReferenceParser.VAR_TYPE;
    JavaParser.INSTANCE.getReferenceParser().parseType(builder, flags);
  };

  public static final JavaParserUtil.ParserWrapper REFERENCE =
    builder -> JavaParser.INSTANCE.getReferenceParser().parseJavaCodeReference(builder, false, true, false, false);

  private static final JavaParserUtil.ParserWrapper DIAMOND_REF =
    builder -> JavaParser.INSTANCE.getReferenceParser().parseJavaCodeReference(builder, false, true, false, true);

  private static final JavaParserUtil.ParserWrapper STATIC_IMPORT_REF =
    builder -> JavaParser.INSTANCE.getReferenceParser().parseImportCodeReference(builder, true);

  private static final JavaParserUtil.ParserWrapper TYPE_PARAMETER =
    builder -> JavaParser.INSTANCE.getReferenceParser().parseTypeParameter(builder);

  private static final JavaParserUtil.ParserWrapper DECLARATION =
    builder -> JavaParser.INSTANCE.getDeclarationParser().parse(builder, DeclarationParser.Context.CLASS);

  private static final JavaParserUtil.ParserWrapper CODE_BLOCK =
    builder -> JavaParser.INSTANCE.getStatementParser().parseCodeBlockDeep(builder, true);

  private static final JavaParserUtil.ParserWrapper STATEMENT = builder -> JavaParser.INSTANCE.getStatementParser().parseStatement(builder);

  private static final JavaParserUtil.ParserWrapper EXPRESSION = builder -> JavaParser.INSTANCE.getExpressionParser().parse(builder);

  private static final JavaParserUtil.ParserWrapper ENUM_CONSTANT =
    builder -> JavaParser.INSTANCE.getDeclarationParser().parseEnumConstant(builder);

  private static final JavaParserUtil.ParserWrapper MODULE = builder -> JavaParser.INSTANCE.getModuleParser().parse(builder);

  private static final Map<String, PsiPrimitiveType> PRIMITIVE_TYPES;
  static {
    PRIMITIVE_TYPES = new HashMap<>();
    PRIMITIVE_TYPES.put(PsiTypes.byteType().getCanonicalText(), PsiTypes.byteType());
    PRIMITIVE_TYPES.put(PsiTypes.charType().getCanonicalText(), PsiTypes.charType());
    PRIMITIVE_TYPES.put(PsiTypes.doubleType().getCanonicalText(), PsiTypes.doubleType());
    PRIMITIVE_TYPES.put(PsiTypes.floatType().getCanonicalText(), PsiTypes.floatType());
    PRIMITIVE_TYPES.put(PsiTypes.intType().getCanonicalText(), PsiTypes.intType());
    PRIMITIVE_TYPES.put(PsiTypes.longType().getCanonicalText(), PsiTypes.longType());
    PRIMITIVE_TYPES.put(PsiTypes.shortType().getCanonicalText(), PsiTypes.shortType());
    PRIMITIVE_TYPES.put(PsiTypes.booleanType().getCanonicalText(), PsiTypes.booleanType());
    PRIMITIVE_TYPES.put(PsiTypes.voidType().getCanonicalText(), PsiTypes.voidType());
    PRIMITIVE_TYPES.put(PsiTypes.nullType().getCanonicalText(), (PsiPrimitiveType)PsiTypes.nullType());
  }

  protected final PsiManager myManager;

  public PsiJavaParserFacadeImpl(@NotNull Project project) {
    myManager = PsiManager.getInstance(project);
  }

  protected PsiJavaFile createDummyJavaFile(String text) {
    return (PsiJavaFile)PsiFileFactory.getInstance(myManager.getProject()).createFileFromText(DUMMY_FILE_NAME, JavaFileType.INSTANCE, text);
  }

  @NotNull
  @Override
  public PsiAnnotation createAnnotationFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, ANNOTATION, level(context)), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiAnnotation)) {
      throw newException("Incorrect annotation '" + text + "'", holder);
    }
    return (PsiAnnotation)element;
  }

  @NotNull
  @Override
  public PsiDocTag createDocTagFromText(@NotNull String text) throws IncorrectOperationException {
    return createDocCommentFromText(StringUtil.join("/**\n", text, "\n */")).getTags()[0];
  }

  @NotNull
  @Override
  public PsiDocComment createDocCommentFromText(@NotNull String text) throws IncorrectOperationException {
    return createDocCommentFromText(text, null);
  }

  @NotNull
  @Override
  public PsiDocComment createDocCommentFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    PsiMethod method = createMethodFromText(text.trim() + "void m();", context);
    PsiDocComment comment = method.getDocComment();
    if (comment == null) {
      throw new IncorrectOperationException("Incorrect comment '" + text + "'");
    }
    return comment;
  }

  @NotNull
  @Override
  public PsiClass createClassFromText(@NotNull String body, @Nullable PsiElement context) throws IncorrectOperationException {
    PsiJavaFile aFile = createDummyJavaFile(StringUtil.join("class _Dummy_ {\n", body, "\n}"));
    PsiClass[] classes = aFile.getClasses();
    if (classes.length != 1) {
      throw new IncorrectOperationException("Incorrect class '" + body + "'");
    }
    return classes[0];
  }

  @NotNull
  @Override
  public PsiImplicitClass createImplicitClassFromText(@NotNull String body, @Nullable PsiElement context) throws IncorrectOperationException {
    PsiJavaFile aFile = createDummyJavaFile(
      "int i = 0;" +  //used to preserve first comments
      body);
    PsiClass[] classes = aFile.getClasses();
    if (classes.length != 1) {
      throw new IncorrectOperationException("Incorrect class '" + body + "'");
    }
    if (classes[0] instanceof PsiImplicitClass) {
      PsiImplicitClass implicitClass = (PsiImplicitClass)classes[0];
      implicitClass.getFirstChild().delete(); //delete stub field
      return implicitClass;
    }
    throw new IncorrectOperationException("Incorrect implicit class '" + body + "'");
  }

  @NotNull
  public PsiClass createRecord(@NotNull String name) throws IncorrectOperationException {
    return createRecordFromText("public record " + name + "() { }");
  }

  @NotNull
  @Override
  public PsiRecordHeader createRecordHeaderFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    PsiRecordHeader header = createRecordFromText("public record Record(" + text + ") { }").getRecordHeader();
    if (header == null) {
      throw new IncorrectOperationException("Incorrect record component '" + text + "'");
    }
    return header;
  }

  private @NotNull PsiClass createRecordFromText(@NotNull String text) {
    JavaDummyElement dummyElement = new JavaDummyElement(text, DECLARATION, LanguageLevel.JDK_16);
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, dummyElement, null);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiClass)) {
      throw newException("Incorrect class '" + text + "'", holder);
    }
    return (PsiClass)element;
  }

  @NotNull
  @Override
  public PsiField createFieldFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, DECLARATION, level(context)), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiField)) {
      throw newException("Incorrect field '" + text + "'", holder);
    }
    return (PsiField)element;
  }

  @NotNull
  @Override
  public PsiMethod createMethodFromText(@NotNull String text, @Nullable PsiElement context, LanguageLevel level) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, DECLARATION, level), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiMethod)) {
      throw newException("Incorrect method '" + text + "'", holder);
    }
    return (PsiMethod)element;
  }

  @NotNull
  @Override
  public PsiMethod createMethodFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    LanguageLevel level = LanguageLevelProjectExtension.getInstance(myManager.getProject()).getLanguageLevel();
    return createMethodFromText(text, context, level);
  }

  @NotNull
  @Override
  public PsiParameter createParameterFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, PARAMETER, level(context)), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiParameter)) {
      throw newException("Incorrect parameter '" + text + "'", holder);
    }
    return (PsiParameter)element;
  }

  @NotNull
  @Override
  public PsiResourceVariable createResourceFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, RESOURCE, level(context)), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiResourceVariable)) {
      throw newException("Incorrect resource '" + text + "'", holder);
    }
    return (PsiResourceVariable)element;
  }

  @NotNull
  @Override
  public PsiType createTypeFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    return createTypeInner(text, context, false);
  }

  @NotNull
  @Override
  public PsiTypeElement createTypeElementFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    LanguageLevel level = level(context);
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, TYPE, level), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiTypeElement)) {
      throw newException("Incorrect type '" + text + "' (" + level + ")", holder);
    }
    return (PsiTypeElement)element;
  }

  PsiType createTypeInner(String text, @Nullable PsiElement context, boolean markAsCopy) throws IncorrectOperationException {
    PsiPrimitiveType primitiveType = PRIMITIVE_TYPES.get(text);
    if (primitiveType != null) return primitiveType;

    PsiTypeElement element = createTypeElementFromText(text, context);
    if (markAsCopy) {
      GeneratedMarkerVisitor.markGenerated(element);
    }
    return element.getType();
  }

  @NotNull
  @Override
  public PsiJavaCodeReferenceElement createReferenceFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    boolean isStaticImport = context instanceof PsiImportStaticStatement && !((PsiImportStaticStatement)context).isOnDemand();
    boolean mayHaveDiamonds = context instanceof PsiNewExpression && PsiUtil.getLanguageLevel(context).isAtLeast(LanguageLevel.JDK_1_7);
    JavaParserUtil.ParserWrapper wrapper = isStaticImport ? STATIC_IMPORT_REF : mayHaveDiamonds ? DIAMOND_REF : REFERENCE;
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, wrapper, level(context)), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiJavaCodeReferenceElement)) {
      throw newException("Incorrect reference '" + text + "'", holder);
    }
    if (context instanceof PsiIdentifier) {
      context = context.getParent();
    }
    if (element instanceof PsiJavaCodeReferenceElementImpl && context instanceof PsiJavaCodeReferenceElementImpl) {
      ((PsiJavaCodeReferenceElementImpl)element).setKindWhenDummy(((PsiJavaCodeReferenceElementImpl)context).getKindEnum(context.getContainingFile()));
    }
    return (PsiJavaCodeReferenceElement)element;
  }

  @NotNull
  @Override
  public PsiCodeBlock createCodeBlockFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, CODE_BLOCK, level(context), true), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiCodeBlock)) {
      throw newException("Incorrect code block '" + text + "'", holder);
    }
    return (PsiCodeBlock)element;
  }

  @NotNull
  @Override
  public PsiStatement createStatementFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, STATEMENT, level(context)), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiStatement)) {
      throw newException("Incorrect statement '" + text + "'", holder);
    }
    return (PsiStatement)element;
  }

  @NotNull
  @Override
  public PsiExpression createExpressionFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, EXPRESSION, level(context)), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiExpression)) {
      throw newException("Incorrect expression '" + text + "'", holder);
    }
    return (PsiExpression)element;
  }

  @NotNull
  @Override
  public PsiTypeParameter createTypeParameterFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, TYPE_PARAMETER, level(context)), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiTypeParameter)) {
      throw newException("Incorrect type parameter '" + text + "'", holder);
    }
    return (PsiTypeParameter)element;
  }

  @NotNull
  @Override
  public PsiComment createCommentFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    PsiJavaFile aFile = createDummyJavaFile(text);
    for (PsiElement aChildren : aFile.getChildren()) {
      if (aChildren instanceof PsiComment) {
        if (!aChildren.getText().equals(text)) {
          break;
        }
        PsiComment comment = (PsiComment)aChildren;
        DummyHolderFactory.createHolder(myManager, (TreeElement)SourceTreeToPsiMap.psiElementToTree(comment), context);
        return comment;
      }
    }

    throw new IncorrectOperationException("Incorrect comment '" + text + "'");
  }

  @NotNull
  @Override
  public PsiEnumConstant createEnumConstantFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, ENUM_CONSTANT, level(context)), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiEnumConstant)) {
      throw newException("Incorrect enum constant '" + text + "'", holder);
    }
    return (PsiEnumConstant)element;
  }

  @Override
  @NotNull
  public PsiType createPrimitiveTypeFromText(@NotNull String text) throws IncorrectOperationException {
    PsiPrimitiveType primitiveType = getPrimitiveType(text);
    if (primitiveType == null) throw new IncorrectOperationException("Incorrect primitive type '" + text + "'");
    return primitiveType;
  }

  @NotNull
  @Override
  public PsiJavaModule createModuleFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, MODULE, LanguageLevel.JDK_1_9), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiJavaModule)) {
      throw newException("Incorrect module declaration '" + text + "'", holder);
    }
    return (PsiJavaModule)element;
  }

  @NotNull
  @Override
  public PsiStatement createModuleStatementFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    String template = "module M { " + text + "; }";
    PsiJavaModule module = createModuleFromText(template, context);
    PsiStatement statement = PsiTreeUtil.getChildOfType(module, PsiStatement.class);
    if (statement == null) throw new IncorrectOperationException("Incorrect module statement '" + text + "'");
    return statement;
  }

  @NotNull
  @Override
  public PsiJavaModuleReferenceElement createModuleReferenceFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    return createModuleFromText("module " + text + " {}", context).getNameIdentifier();
  }

  public static PsiPrimitiveType getPrimitiveType(String text) {
    return PRIMITIVE_TYPES.get(text);
  }

  protected static LanguageLevel level(@Nullable PsiElement context) {
    return context != null && context.isValid() ? PsiUtil.getLanguageLevel(context) : LanguageLevel.HIGHEST;
  }

  private static IncorrectOperationException newException(@NonNls String msg, DummyHolder holder) {
    FileElement root = holder.getTreeElement();
    if (root instanceof JavaDummyElement) {
      Throwable cause = ((JavaDummyElement)root).getParserError();
      if (cause != null) {
        return new IncorrectOperationException(msg, cause);
      }
    }
    return new IncorrectOperationException(msg);
  }
}