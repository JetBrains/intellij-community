// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.parser.DeclarationParser;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.lang.java.parser.ReferenceParser;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.JavaDummyElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
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

/**
 * @author max
 */
public class PsiJavaParserFacadeImpl implements PsiJavaParserFacade {
  private static final String DUMMY_FILE_NAME = "_Dummy_." + JavaFileType.INSTANCE.getDefaultExtension();

  private static final JavaParserUtil.ParserWrapper ANNOTATION =
    builder -> JavaParser.INSTANCE.getDeclarationParser().parseAnnotation(builder);

  private static final JavaParserUtil.ParserWrapper PARAMETER =
    builder -> JavaParser.INSTANCE.getDeclarationParser().parseParameter(builder, true, false, false);

  private static final JavaParserUtil.ParserWrapper RESOURCE = builder -> JavaParser.INSTANCE.getDeclarationParser().parseResource(builder);

  private static final JavaParserUtil.ParserWrapper TYPE = builder -> {
    int flags = ReferenceParser.EAT_LAST_DOT | ReferenceParser.ELLIPSIS | ReferenceParser.WILDCARD | ReferenceParser.DISJUNCTIONS | ReferenceParser.VAR_TYPE;
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
    PRIMITIVE_TYPES.put(PsiType.BYTE.getCanonicalText(), PsiType.BYTE);
    PRIMITIVE_TYPES.put(PsiType.CHAR.getCanonicalText(), PsiType.CHAR);
    PRIMITIVE_TYPES.put(PsiType.DOUBLE.getCanonicalText(), PsiType.DOUBLE);
    PRIMITIVE_TYPES.put(PsiType.FLOAT.getCanonicalText(), PsiType.FLOAT);
    PRIMITIVE_TYPES.put(PsiType.INT.getCanonicalText(), PsiType.INT);
    PRIMITIVE_TYPES.put(PsiType.LONG.getCanonicalText(), PsiType.LONG);
    PRIMITIVE_TYPES.put(PsiType.SHORT.getCanonicalText(), PsiType.SHORT);
    PRIMITIVE_TYPES.put(PsiType.BOOLEAN.getCanonicalText(), PsiType.BOOLEAN);
    PRIMITIVE_TYPES.put(PsiType.VOID.getCanonicalText(), PsiType.VOID);
    PRIMITIVE_TYPES.put(PsiType.NULL.getCanonicalText(), PsiType.NULL);
  }

  protected final PsiManager myManager;

  public PsiJavaParserFacadeImpl(PsiManager manager) {
    myManager = manager;
  }

  @NotNull
  @Override
  public PsiAnnotation createAnnotationFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, ANNOTATION, level(context)), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiAnnotation)) {
      throw new IncorrectOperationException("Incorrect annotation '" + text + "'");
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
  public PsiDocComment createDocCommentFromText(@NotNull String docCommentText) throws IncorrectOperationException {
    return createDocCommentFromText(docCommentText, null);
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
  public PsiField createFieldFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, DECLARATION, level(context)), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiField)) {
      throw new IncorrectOperationException("Incorrect field '" + text + "'");
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
      throw new IncorrectOperationException("Incorrect parameter '" + text + "'");
    }
    return (PsiParameter)element;
  }

  @NotNull
  @Override
  public PsiResourceVariable createResourceFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, RESOURCE, level(context)), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiResourceVariable)) {
      throw new IncorrectOperationException("Incorrect resource '" + text + "'");
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
      throw new IncorrectOperationException("Incorrect type '" + text + "' (" + level + ")");
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
      throw new IncorrectOperationException("Incorrect reference '" + text + "'");
    }
    return (PsiJavaCodeReferenceElement)element;
  }

  @NotNull
  @Override
  public PsiCodeBlock createCodeBlockFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, CODE_BLOCK, level(context), true), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiCodeBlock)) {
      throw new IncorrectOperationException("Incorrect code block '" + text + "'");
    }
    return (PsiCodeBlock)element;
  }

  @NotNull
  @Override
  public PsiStatement createStatementFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, STATEMENT, level(context)), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiStatement)) {
      throw new IncorrectOperationException("Incorrect statement '" + text + "'");
    }
    return (PsiStatement)element;
  }

  @NotNull
  @Override
  public PsiExpression createExpressionFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, EXPRESSION, level(context)), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiExpression)) {
      throw new IncorrectOperationException("Incorrect expression '" + text + "'");
    }
    return (PsiExpression)element;
  }

  PsiJavaFile createDummyJavaFile(@NonNls String text) {
    return (PsiJavaFile)PsiFileFactory.getInstance(myManager.getProject()).createFileFromText(DUMMY_FILE_NAME, JavaFileType.INSTANCE, text);
  }

  @NotNull
  @Override
  public PsiTypeParameter createTypeParameterFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, TYPE_PARAMETER, level(context)), context);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiTypeParameter)) {
      throw new IncorrectOperationException("Incorrect type parameter '" + text + "'");
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
      throw new IncorrectOperationException("Incorrect enum constant '" + text + "'");
    }
    return (PsiEnumConstant)element;
  }

  @Override
  @NotNull
  public PsiType createPrimitiveTypeFromText(@NotNull String text) throws IncorrectOperationException {
    PsiPrimitiveType primitiveType = getPrimitiveType(text);
    if (primitiveType == null) {
      throw new IncorrectOperationException("Incorrect primitive type '" + text + "'");
    }
    return primitiveType;
  }

  @NotNull
  @Override
  public PsiJavaModule createModuleFromText(@NotNull String text) throws IncorrectOperationException {
    DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, MODULE, LanguageLevel.JDK_1_9), null);
    PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiJavaModule)) throw new IncorrectOperationException("Incorrect module declaration '" + text + "'");
    return (PsiJavaModule)element;
  }

  @NotNull
  @Override
  public PsiStatement createModuleStatementFromText(@NotNull String text) throws IncorrectOperationException {
    String template = "module M { " + text + "; }";
    PsiJavaModule module = createModuleFromText(template);
    PsiStatement statement = PsiTreeUtil.getChildOfType(module, PsiStatement.class);
    if (statement == null) throw new IncorrectOperationException("Incorrect module statement '" + text + "'");
    return statement;
  }

  public static PsiPrimitiveType getPrimitiveType(String text) {
    return PRIMITIVE_TYPES.get(text);
  }

  protected static LanguageLevel level(@Nullable PsiElement context) {
    return context != null && context.isValid() ? PsiUtil.getLanguageLevel(context) : LanguageLevel.HIGHEST;
  }

  private static IncorrectOperationException newException(String msg, DummyHolder holder) {
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