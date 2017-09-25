/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.*;
import com.intellij.openapi.fileTypes.FileType;
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
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author max
 */
public class PsiJavaParserFacadeImpl implements PsiJavaParserFacade {
  protected final PsiManager myManager;

  private static final String DUMMY_FILE_NAME = "_Dummy_." + JavaFileType.INSTANCE.getDefaultExtension();

  public PsiJavaParserFacadeImpl(PsiManager manager) {
    myManager = manager;
  }

  private static final JavaParserUtil.ParserWrapper ANNOTATION = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getDeclarationParser().parseAnnotation(builder);
    }
  };

  private static final JavaParserUtil.ParserWrapper PARAMETER = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getDeclarationParser().parseParameter(builder, true, false, false);
    }
  };

  private static final JavaParserUtil.ParserWrapper RESOURCE = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getDeclarationParser().parseResource(builder);
    }
  };

  private static final JavaParserUtil.ParserWrapper TYPE = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      int flags = ReferenceParser.EAT_LAST_DOT | ReferenceParser.ELLIPSIS | ReferenceParser.WILDCARD | ReferenceParser.DISJUNCTIONS | ReferenceParser.VAR_TYPE;
      JavaParser.INSTANCE.getReferenceParser().parseType(builder, flags);
    }
  };

  public static final JavaParserUtil.ParserWrapper REFERENCE = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getReferenceParser().parseJavaCodeReference(builder, false, true, false, false);
    }
  };

  public static final JavaParserUtil.ParserWrapper DIAMOND_REF = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getReferenceParser().parseJavaCodeReference(builder, false, true, false, true);
    }
  };

  public static final JavaParserUtil.ParserWrapper STATIC_IMPORT_REF = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getReferenceParser().parseImportCodeReference(builder, true);
    }
  };

  private static final JavaParserUtil.ParserWrapper TYPE_PARAMETER = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getReferenceParser().parseTypeParameter(builder);
    }
  };

  private static final JavaParserUtil.ParserWrapper DECLARATION = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getDeclarationParser().parse(builder, DeclarationParser.Context.CLASS);
    }
  };

  private static final JavaParserUtil.ParserWrapper CODE_BLOCK = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getStatementParser().parseCodeBlockDeep(builder, true);
    }
  };

  private static final JavaParserUtil.ParserWrapper STATEMENT = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getStatementParser().parseStatement(builder);
    }
  };

  private static final JavaParserUtil.ParserWrapper EXPRESSION = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getExpressionParser().parse(builder);
    }
  };

  private static final JavaParserUtil.ParserWrapper ENUM_CONSTANT = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getDeclarationParser().parseEnumConstant(builder);
    }
  };

  private static final JavaParserUtil.ParserWrapper MODULE = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getModuleParser().parse(builder);
    }
  };

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

  @NotNull
  @Override
  public PsiAnnotation createAnnotationFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, ANNOTATION, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiAnnotation)) {
      throw new IncorrectOperationException("Incorrect annotation '" + text + "'");
    }
    return (PsiAnnotation)element;
  }

  @NotNull
  @Override
  public PsiDocTag createDocTagFromText(@NotNull final String text) throws IncorrectOperationException {
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
    final PsiMethod method = createMethodFromText(text.trim() + "void m();", context);
    final PsiDocComment comment = method.getDocComment();
    if (comment == null) {
      throw new IncorrectOperationException("Incorrect comment '" + text + "'");
    }
    return comment;
  }

  @NotNull
  @Override
  public PsiClass createClassFromText(@NotNull final String body, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiJavaFile aFile = createDummyJavaFile(StringUtil.join("class _Dummy_ {\n", body, "\n}"));
    final PsiClass[] classes = aFile.getClasses();
    if (classes.length != 1) {
      throw new IncorrectOperationException("Incorrect class '" + body + "'");
    }
    return classes[0];
  }

  @NotNull
  @Override
  public PsiField createFieldFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, DECLARATION, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiField)) {
      throw new IncorrectOperationException("Incorrect field '" + text + "'");
    }
    return (PsiField)element;
  }

  @NotNull
  @Override
  public PsiMethod createMethodFromText(@NotNull final String text, @Nullable final PsiElement context, final LanguageLevel level) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, DECLARATION, level), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiMethod)) {
      throw newException("Incorrect method '" + text + "'", holder);
    }
    return (PsiMethod)element;
  }

  @NotNull
  @Override
  public final PsiMethod createMethodFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final LanguageLevel level = LanguageLevelProjectExtension.getInstance(myManager.getProject()).getLanguageLevel();
    return createMethodFromText(text, context, level);
  }

  @NotNull
  @Override
  public PsiParameter createParameterFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, PARAMETER, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiParameter)) {
      throw new IncorrectOperationException("Incorrect parameter '" + text + "'");
    }
    return (PsiParameter)element;
  }

  @NotNull
  @Override
  public PsiResourceVariable createResourceFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, RESOURCE, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiResourceVariable)) {
      throw new IncorrectOperationException("Incorrect resource '" + text + "'");
    }
    return (PsiResourceVariable)element;
  }

  @NotNull
  @Override
  public PsiType createTypeFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    return createTypeInner(text, context, false);
  }

  @NotNull
  @Override
  public PsiTypeElement createTypeElementFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final LanguageLevel level = level(context);
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, TYPE, level), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiTypeElement)) {
      throw new IncorrectOperationException("Incorrect type '" + text + "' (" + level + ")");
    }
    return (PsiTypeElement)element;
  }

  protected PsiType createTypeInner(final String text, @Nullable final PsiElement context, final boolean markAsCopy) throws IncorrectOperationException {
    final PsiPrimitiveType primitiveType = PRIMITIVE_TYPES.get(text);
    if (primitiveType != null) return primitiveType;

    final PsiTypeElement element = createTypeElementFromText(text, context);
    if (markAsCopy) {
      GeneratedMarkerVisitor.markGenerated(element);
    }
    return element.getType();
  }

  @NotNull
  @Override
  public PsiJavaCodeReferenceElement createReferenceFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final boolean isStaticImport = context instanceof PsiImportStaticStatement &&
                                   !((PsiImportStaticStatement)context).isOnDemand();
    final boolean mayHaveDiamonds = context instanceof PsiNewExpression &&
                                    PsiUtil.getLanguageLevel(context).isAtLeast(LanguageLevel.JDK_1_7);
    final JavaParserUtil.ParserWrapper wrapper = isStaticImport ? STATIC_IMPORT_REF : mayHaveDiamonds ? DIAMOND_REF : REFERENCE;
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, wrapper, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiJavaCodeReferenceElement)) {
      throw new IncorrectOperationException("Incorrect reference '" + text + "'");
    }
    return (PsiJavaCodeReferenceElement)element;
  }

  @NotNull
  @Override
  public PsiCodeBlock createCodeBlockFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, CODE_BLOCK, level(context), true), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiCodeBlock)) {
      throw new IncorrectOperationException("Incorrect code block '" + text + "'");
    }
    return (PsiCodeBlock)element;
  }

  @NotNull
  @Override
  public PsiStatement createStatementFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, STATEMENT, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiStatement)) {
      throw new IncorrectOperationException("Incorrect statement '" + text + "'");
    }
    return (PsiStatement)element;
  }

  @NotNull
  @Override
  public PsiExpression createExpressionFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, EXPRESSION, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiExpression)) {
      throw new IncorrectOperationException("Incorrect expression '" + text + "'");
    }
    return (PsiExpression)element;
  }

  protected PsiJavaFile createDummyJavaFile(@NonNls final String text) {
    final FileType type = JavaFileType.INSTANCE;
    return (PsiJavaFile)PsiFileFactory.getInstance(myManager.getProject()).createFileFromText(DUMMY_FILE_NAME, type, text);
  }

  @NotNull
  @Override
  public PsiTypeParameter createTypeParameterFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, TYPE_PARAMETER, level(context)),
                                                               context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiTypeParameter)) {
      throw new IncorrectOperationException("Incorrect type parameter '" + text + "'");
    }
    return (PsiTypeParameter)element;
  }

  @NotNull
  @Override
  public PsiComment createCommentFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final PsiJavaFile aFile = createDummyJavaFile(text);
    for (PsiElement aChildren : aFile.getChildren()) {
      if (aChildren instanceof PsiComment) {
        if (!aChildren.getText().equals(text)) {
          break;
        }
        final PsiComment comment = (PsiComment)aChildren;
        DummyHolderFactory.createHolder(myManager, (TreeElement)SourceTreeToPsiMap.psiElementToTree(comment), context);
        return comment;
      }
    }

    throw new IncorrectOperationException("Incorrect comment '" + text + "'");
  }

  @NotNull
  @Override
  public PsiEnumConstant createEnumConstantFromText(@NotNull final String text, @Nullable final PsiElement context) throws IncorrectOperationException {
    final DummyHolder holder = DummyHolderFactory.createHolder(myManager, new JavaDummyElement(text, ENUM_CONSTANT, level(context)), context);
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
    if (!(element instanceof PsiEnumConstant)) {
      throw new IncorrectOperationException("Incorrect enum constant '" + text + "'");
    }
    return (PsiEnumConstant)element;
  }

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
  public PsiType createPrimitiveType(@NotNull String text, @NotNull PsiAnnotation[] annotations) throws IncorrectOperationException {
    return createPrimitiveTypeFromText(text).annotate(TypeAnnotationProvider.Static.create(annotations));
  }

  public static PsiPrimitiveType getPrimitiveType(final String text) {
    return PRIMITIVE_TYPES.get(text);
  }

  protected static LanguageLevel level(@Nullable final PsiElement context) {
    return context != null && context.isValid() ? PsiUtil.getLanguageLevel(context) : LanguageLevel.HIGHEST;
  }

  private static IncorrectOperationException newException(final String msg, final DummyHolder holder) {
    final FileElement root = holder.getTreeElement();
    if (root instanceof JavaDummyElement) {
      final Throwable cause = ((JavaDummyElement)root).getParserError();
      if (cause != null) {
        return new IncorrectOperationException(msg, cause);
      }
    }
    return new IncorrectOperationException(msg);
  }
}