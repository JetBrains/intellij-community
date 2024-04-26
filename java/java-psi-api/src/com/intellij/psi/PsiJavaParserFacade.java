// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PsiJavaParserFacade {
  /**
   * Creates a JavaDoc tag from the specified text.
   *
   * @param text the text of the JavaDoc tag.
   * @return the created tag.
   * @throws IncorrectOperationException if the text of the tag is not valid.
   */
  @NotNull
  PsiDocTag createDocTagFromText(@NotNull @NonNls String text) throws IncorrectOperationException;

  /**
   * Creates a JavaDoc comment from the specified text.
   *
   * @param text the text of the JavaDoc comment.
   * @return the created comment.
   * @throws IncorrectOperationException if the text of the comment is not valid.
   */
  @NotNull
  PsiDocComment createDocCommentFromText(@NotNull @NonNls String text) throws IncorrectOperationException;

  /**
   * Creates a JavaDoc comment from the specified text.
   *
   * @param text the text of the JavaDoc comment.
   * @param context the PSI element used as context for resolving references inside this javadoc
   * @return the created comment.
   * @throws IncorrectOperationException if the text of the comment is not valid.
   */
  @NotNull
  PsiDocComment createDocCommentFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java class with a dummy name from the specified body text (the text between the braces).
   *
   * @param text    the body text of the class to create.
   * @param context the PSI element used as context for resolving references which cannot be resolved
   *                within the class.
   * @return the created class instance.
   * @throws IncorrectOperationException if the text is not a valid class body.
   */
  @NotNull
  PsiClass createClassFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java field from the specified text.
   *
   * @param text    the text of the field to create.
   * @param context the PSI element used as context for resolving references from the field.
   * @return the created field instance.
   * @throws IncorrectOperationException if the text is not a valid field body.
   */
  @NotNull
  PsiField createFieldFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java method from the specified text with the specified language level.
   *
   * @param text          the text of the method to create.
   * @param context       the PSI element used as context for resolving references from the method.
   * @param languageLevel the language level used for creating the method.
   * @return the created method instance.
   * @throws IncorrectOperationException if the text is not a valid method body.
   */
  @NotNull
  PsiMethod createMethodFromText(@NotNull @NonNls String text, @Nullable PsiElement context, LanguageLevel languageLevel) throws IncorrectOperationException;

  /**
   * Creates a Java method from the specified text.
   *
   * @param text    the text of the method to create.
   * @param context the PSI element used as context for resolving references from the method.
   * @return the created method instance.
   * @throws IncorrectOperationException if the text is not a valid method body.
   */
  @NotNull
  PsiMethod createMethodFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java method parameter from the specified text.
   *
   * @param text    the text of the parameter to create.
   * @param context the PSI element used as context for resolving references from the parameter.
   * @return the created parameter instance.
   * @throws IncorrectOperationException if the text is not a valid parameter body.
   */
  @NotNull
  PsiParameter createParameterFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates an implicit class from the specified body text.
   *
   * @param body    the body text of the class to create.
   * @param context the PSI element used as context for resolving references which cannot be resolved
   *                within the class.
   * @return created class instance.
   * @throws IncorrectOperationException if the text is not a valid class body.
   */
  @NotNull
  PsiImplicitClass createImplicitClassFromText(@NotNull String body, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java record header from the specified text (excluding parentheses).
   *
   * @param text    the text of the record header to create.
   * @param context the PSI element used as context for resolving references from the header.
   * @return the created record header instance.
   * @throws IncorrectOperationException if the text is not a valid record header text.
   */
  @NotNull
  PsiRecordHeader createRecordHeaderFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java try-resource from the specified text.
   *
   * @param text    the text of the resource to create.
   * @param context the PSI element used as context for resolving references from the resource.
   * @return the created resource instance.
   * @throws IncorrectOperationException if the text is not a valid resource definition.
   */
  @NotNull
  PsiResourceVariable createResourceFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java type from the specified text.
   *
   * @param text    the text of the type to create (for example, a primitive type keyword, an array
   *                declaration or the name of a class).
   * @param context the PSI element used as context for resolving the reference.
   * @return the created type instance.
   * @throws IncorrectOperationException if the text does not specify a valid type.
   */
  @NotNull
  PsiType createTypeFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java type element from the specified text.
   *
   * @param text    the text of the type to create (for example, a primitive type keyword, an array
   *                declaration or the name of a class).
   * @param context the PSI element used as context for resolving the reference.
   * @return the created type element.
   * @throws IncorrectOperationException if the text does not specify a valid type.
   */
  @NotNull
  PsiTypeElement createTypeElementFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java code reference from the specified text.
   *
   * @param text    the text of the type to create (for example, a primitive type keyword, an array
   *                declaration or the name of a class).
   * @param context the PSI element used as context for resolving the reference.
   * @return the created reference element.
   * @throws IncorrectOperationException if the text does not specify a valid type.
   */
  @NotNull
  PsiJavaCodeReferenceElement createReferenceFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java code block from the specified text.
   *
   * @param text    the text of the code block to create.
   * @param context the PSI element used as context for resolving references from the block.
   * @return the created code block instance.
   * @throws IncorrectOperationException if the text does not specify a valid code block.
   */
  @NotNull
  PsiCodeBlock createCodeBlockFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java statement from the specified text.
   *
   * @param text    the text of the statement to create.
   * @param context the PSI element used as context for resolving references from the statement.
   * @return the created statement instance.
   * @throws IncorrectOperationException if the text does not specify a valid statement.
   */
  @NotNull
  PsiStatement createStatementFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java expression from the specified text.
   *
   * @param text    the text of the expression to create.
   * @param context the PSI element used as context for resolving references from the expression.
   * @return the created expression instance.
   * @throws IncorrectOperationException if the text does not specify a valid expression.
   */
  @NotNull
  PsiExpression createExpressionFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java comment from the specified text.
   *
   * @param text    the text of the comment to create.
   * @param context the PSI element used as context for resolving references from the comment.
   * @return the created comment instance.
   * @throws IncorrectOperationException if the text does not specify a valid comment.
   */
  @NotNull
  PsiComment createCommentFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a type parameter from the specified text.
   *
   * @param text    the text of the type parameter to create.
   * @param context the context for resolving references.
   * @return the created type parameter instance.
   * @throws IncorrectOperationException if the text does not specify a valid type parameter.
   */
  @NotNull
  PsiTypeParameter createTypeParameterFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates an annotation from the specified text.
   *
   * @param annotationText the text of the annotation to create.
   * @param context        the context for resolving references from the annotation.
   * @return the created annotation instance.
   * @throws IncorrectOperationException if the text does not specify a valid annotation.
   */
  @NotNull
  PsiAnnotation createAnnotationFromText(@NotNull @NonNls String annotationText, @Nullable PsiElement context) throws IncorrectOperationException;

  @NotNull
  PsiEnumConstant createEnumConstantFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java type from the specified text.
   *
   * @param text the text of the type to create (a primitive type keyword).
   * @return the created type instance.
   * @throws IncorrectOperationException if some of the parameters are not valid.
   */
  @NotNull
  PsiType createPrimitiveTypeFromText(@NotNull @NonNls String text) throws IncorrectOperationException;

  /**
   * Creates a Java module declaration from the specified text.
   */
  @NotNull
  PsiJavaModule createModuleFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java module statement from the specified text.
   */
  @NotNull
  PsiStatement createModuleStatementFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java module reference element from the specified text.
   */
  @NotNull
  PsiJavaModuleReferenceElement createModuleReferenceFromText(@NotNull @NonNls String text, @Nullable PsiElement context) throws IncorrectOperationException;
}