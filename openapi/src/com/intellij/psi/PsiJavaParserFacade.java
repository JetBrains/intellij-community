/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public interface PsiJavaParserFacade {
  /**
   * Creates a JavaDoc tag from the specified text.
   *
   * @param docTagText the text of the JavaDoc tag.
   * @param context    ignored; no longer used
   * @return the created tag.
   * @throws com.intellij.util.IncorrectOperationException if the text of the tag is not valid.
   */
  @NotNull
  PsiDocTag createDocTagFromText(@NotNull String docTagText, @Deprecated PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a JavaDoc comment from the specified text.
   *
   * @param docCommentText the text of the JavaDoc comment.
   * @param context        ignored; no longer used
   * @return the created comment.
   * @throws com.intellij.util.IncorrectOperationException if the text of the comment is not valid.
   */
  @NotNull
  PsiDocComment createDocCommentFromText(@NotNull String docCommentText, @Deprecated PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a file from the specified text.
   *
   * @param name the name of the file to create (the extension of the name determines the file type).
   * @param text the text of the file to create.
   * @return the created file.
   * @throws com.intellij.util.IncorrectOperationException if the file type with specified extension is binary.
   */
  @NotNull
  PsiFile createFileFromText(@NotNull @NonNls String name, @NotNull @NonNls String text);

  /**
   * Creates a Java class from the specified text.
   *
   * @param text    the text of the class to create.
   * @param context the PSI element used as context for resolving references which cannot be resolved
   *                within the class.
   * @return the created class instance.
   * @throws com.intellij.util.IncorrectOperationException if the text is not a valid class body.
   */
  @NotNull
  PsiClass createClassFromText(@NotNull @NonNls String text, PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java field from the specified text.
   *
   * @param text    the text of the field to create.
   * @param context the PSI element used as context for resolving references from the field.
   * @return the created field instance.
   * @throws com.intellij.util.IncorrectOperationException if the text is not a valid field body.
   */
  @NotNull
  PsiField createFieldFromText(@NotNull @NonNls String text, PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java method from the specified text with the specified language level.
   *
   * @param text          the text of the method to create.
   * @param context       the PSI element used as context for resolving references from the method.
   * @param languageLevel the language level used for creating the method.
   * @return the created method instance.
   * @throws com.intellij.util.IncorrectOperationException if the text is not a valid method body.
   */
  @NotNull
  PsiMethod createMethodFromText(@NotNull @NonNls String text, PsiElement context, LanguageLevel languageLevel) throws IncorrectOperationException;

  /**
   * Creates a Java method from the specified text.
   *
   * @param text    the text of the method to create.
   * @param context the PSI element used as context for resolving references from the method.
   * @return the created method instance.
   * @throws com.intellij.util.IncorrectOperationException if the text is not a valid method body.
   */
  @NotNull
  PsiMethod createMethodFromText(@NotNull @NonNls String text, PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java parameter from the specified text.
   *
   * @param text    the text of the parameter to create.
   * @param context the PSI element used as context for resolving references from the parameter.
   * @return the created parameter instance.
   * @throws com.intellij.util.IncorrectOperationException if the text is not a valid parameter body.
   */
  @NotNull
  PsiParameter createParameterFromText(@NotNull @NonNls String text, PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java type from the specified text.
   *
   * @param text    the text of the type to create (for example, a primitive type keyword, an array
   *                declaration or the name of a class)
   * @param context the PSI element used as context for resolving the reference.
   * @return the created type instance.
   * @throws com.intellij.util.IncorrectOperationException if the text does not specify a valid type.
   */
  @NotNull
  PsiType createTypeFromText(@NotNull @NonNls String text, PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java code block from the specified text.
   *
   * @param text    the text of the code block to create.
   * @param context the PSI element used as context for resolving references from the block.
   * @return the created code block instance.
   * @throws com.intellij.util.IncorrectOperationException if the text does not specify a valid code block.
   */
  @NotNull
  PsiCodeBlock createCodeBlockFromText(@NotNull @NonNls String text, PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java statement from the specified text.
   *
   * @param text    the text of the statement to create.
   * @param context the PSI element used as context for resolving references from the statement.
   * @return the created statement instance.
   * @throws com.intellij.util.IncorrectOperationException if the text does not specify a valid statement.
   */
  @NotNull
  PsiStatement createStatementFromText(@NotNull @NonNls String text, PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java expression from the specified text.
   *
   * @param text    the text of the expression to create.
   * @param context the PSI element used as context for resolving references from the expression.
   * @return the created expression instance.
   * @throws com.intellij.util.IncorrectOperationException if the text does not specify a valid expression.
   */
  @NotNull
  PsiExpression createExpressionFromText(@NotNull @NonNls String text, PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java comment from the specified text.
   *
   * @param text    the text of the comment to create.
   * @param context the PSI element used as context for resolving references from the comment.
   * @return the created comment instance.
   * @throws com.intellij.util.IncorrectOperationException if the text does not specify a valid comment.
   */
  @NotNull
  PsiComment createCommentFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a type parameter from the specified text.
   *
   * @param text    the text of the type parameter to create.
   * @param context the context for resolving references.
   * @return the created type parameter instance.
   * @throws com.intellij.util.IncorrectOperationException if the text does not specify a valid type parameter.
   */
  @NotNull
  PsiTypeParameter createTypeParameterFromText(@NotNull @NonNls String text, PsiElement context) throws IncorrectOperationException;

  /**
   * Creates an annotation from the specified text.
   *
   * @param annotationText the text of the annotation to create.
   * @param context        the context for resolving references from the annotation.
   * @return the created annotation instance.
   * @throws com.intellij.util.IncorrectOperationException if the text does not specify a valid annotation.
   */
  @NotNull
  PsiAnnotation createAnnotationFromText(@NotNull @NonNls String annotationText, PsiElement context) throws IncorrectOperationException;

  /**
   * Creates an PsiWhiteSpace with the specified text.
   *
   * @param s the text of whitespace
   * @return the created whitespace instance.
   * @throws com.intellij.util.IncorrectOperationException if the text does not specify a valid whitespace.
   */
  @NotNull
  PsiElement createWhiteSpaceFromText(@NotNull @NonNls String s) throws IncorrectOperationException;

  @NotNull
  PsiFile createFileFromText(@NonNls @NotNull String fileName, @NotNull FileType fileType, @NotNull CharSequence text);

  @NotNull
  PsiFile createFileFromText(@NonNls @NotNull String name, @NotNull FileType fileType, @NotNull CharSequence text,
                                      long modificationStamp, boolean physical);

  @NotNull
  PsiFile createFileFromText(@NonNls @NotNull String name, @NotNull FileType fileType, @NotNull CharSequence text,
                                      long modificationStamp, boolean physical, boolean markAsCopy);

  PsiFile createFileFromText(@NotNull String name, @NotNull Language language, @NotNull String text);

  @NotNull
  PsiEnumConstant createEnumConstantFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException;


  /**
   * Creates a <code>catch</code> section for catching an exception of the specified
   * type and name.
   *
   * @param exceptionType the type of the exception to catch.
   * @param exceptionName the name of the variable in which the caught exception is stored (may be an empty string)
   * @param context       the context for resolving references.
   * @return the created catch section instance.
   * @throws IncorrectOperationException if some of the parameters are not valid.
   */
  @NotNull PsiCatchSection createCatchSection(@NotNull PsiClassType exceptionType, @NotNull String exceptionName, PsiElement context)
    throws IncorrectOperationException;
}
