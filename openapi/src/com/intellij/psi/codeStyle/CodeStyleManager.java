/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Service for reformatting code fragments, getting names for elements
 * according to the user's code style and working with import statements and full-qualified names.
 *
 * @see com.intellij.psi.PsiManager#getCodeStyleManager()
 */
public abstract class CodeStyleManager {
  /**
   * Returns the code style manager for the specified project.
   *
   * @param project the project to get the code style manager for.
   * @return the code style manager instance.
   */
  public static CodeStyleManager getInstance(@NotNull Project project) {
    return project.getComponent(CodeStyleManager.class);
  }

  /**
   * Returns the code style manager for the project associated with the specified
   * PSI manager.
   *
   * @param manager the PSI manager to get the code style manager for.
   * @return the code style manager instance.
   */
  public static CodeStyleManager getInstance(@NotNull PsiManager manager) {
    return getInstance(manager.getProject());
  }

  /**
   * Gets the project with which the code style manager is associated.
   *
   * @return the project instance.
   */
  @NotNull public abstract Project getProject();

  /**
   * Reformats the contents of the specified PSI element, enforces braces and splits import
   * statements according to the user's code style.
   *
   * @param element the element to reformat.
   * @return the element in the PSI tree after the reformat operation corresponding to the
   *         original element.
   * @throws IncorrectOperationException if the file to reformat is read-only.
   * @see #reformatText(com.intellij.psi.PsiFile, int, int)
   */
  @NotNull public abstract PsiElement reformat(@NotNull PsiElement element) throws IncorrectOperationException;

  /**
   * Reformats the contents of the specified PSI element, and optionally enforces braces
   * and splits import statements according to the user's code style.
   *
   * @param element                  the element to reformat.
   * @param canChangeWhiteSpacesOnly if true, only reformatting is performed; if false,
   *                                 braces and import statements also can be modified if necessary.
   * @return the element in the PSI tree after the reformat operation corresponding to the
   *         original element.
   * @throws IncorrectOperationException if the file to reformat is read-only.
   * @see #reformatText(com.intellij.psi.PsiFile, int, int)
   */
  @NotNull public abstract PsiElement reformat(@NotNull PsiElement element, boolean canChangeWhiteSpacesOnly) throws IncorrectOperationException;

  /**
   * Reformats part of the contents of the specified PSI element, enforces braces
   * and splits import statements according to the user's code style.
   *
   * @param element     the element to reformat.
   * @param startOffset the start offset in the document of the text range to reformat.
   * @param endOffset   the end offset in the document of the text range to reformat.
   * @return the element in the PSI tree after the reformat operation corresponding to the
   *         original element.
   * @throws IncorrectOperationException if the file to reformat is read-only.
   * @see #reformatText(com.intellij.psi.PsiFile, int, int)
   */
  public abstract PsiElement reformatRange(@NotNull PsiElement element, int startOffset, int endOffset) throws IncorrectOperationException;

  /**
   * Reformats part of the contents of the specified PSI element, and optionally enforces braces
   * and splits import statements according to the user's code style.
   *
   * @param element                  the element to reformat.
   * @param startOffset              the start offset in the document of the text range to reformat.
   * @param endOffset                the end offset in the document of the text range to reformat.
   * @param canChangeWhiteSpacesOnly if true, only reformatting is performed; if false,
   *                                 braces and import statements also can be modified if necessary.
   * @return the element in the PSI tree after the reformat operation corresponding to the
   *         original element.
   * @throws IncorrectOperationException if the file to reformat is read-only.
   * @see #reformatText(com.intellij.psi.PsiFile, int, int)
   */
  public abstract PsiElement reformatRange(@NotNull PsiElement element,
                                           int startOffset,
                                           int endOffset,
                                           boolean canChangeWhiteSpacesOnly) throws IncorrectOperationException;

  /**
   * Reformats a range of text in the specified file. This method works faster than
   * {@link #reformatRange(com.intellij.psi.PsiElement, int, int)} but invalidates the
   * PSI structure for the file.
   *
   * @param element     the file to reformat.
   * @param startOffset the start of the text range to reformat.
   * @param endOffset   the end of the text range to reformat.
   * @throws IncorrectOperationException if the file to reformat is read-only.
   */
  public abstract void reformatText(@NotNull PsiFile element, int startOffset, int endOffset) throws IncorrectOperationException;

  /**
   * Replaces fully-qualified class names in the contents of the specified element with
   * non-qualified names and adds import statements as necessary.
   *
   * @param element the element to shorten references in.
   * @return the element in the PSI tree after the shorten references operation corresponding
   *         to the original element.
   * @throws IncorrectOperationException if the file to shorten references in is read-only.
   */
  public abstract PsiElement shortenClassReferences(@NotNull PsiElement element) throws IncorrectOperationException;

  /**
   * Replaces fully-qualified class names in a part of contents of the specified element with
   * non-qualified names and adds import statements as necessary.
   *
   * @param element     the element to shorten references in.
   * @param startOffset the start offset in the <b>element</b> of the part where class references are
   *                    shortened.
   * @param endOffset   the end offset in the <b>element</b> of the part where class references are
   *                    shortened.
   * @throws IncorrectOperationException if the file to shorten references in is read-only.
   */
  public abstract void shortenClassReferences(@NotNull PsiElement element, int startOffset, int endOffset) throws IncorrectOperationException;

  /**
   * Optimizes imports in the specified Java or JSP file.
   *
   * @param file the file to optimize the imports in.
   * @throws IncorrectOperationException if the file is read-only.
   */
  public abstract void optimizeImports(@NotNull PsiFile file) throws IncorrectOperationException;

  /**
   * Calculates the import list that would be substituted in the specified Java or JSP
   * file if an Optimize Imports operation was performed on it.
   *
   * @param file the file to calculate the import list for.
   * @return the calculated import list.
   */
  public abstract PsiImportList prepareOptimizeImportsResult(@NotNull PsiJavaFile file);

  /**
   * Reformats the specified range of a file, modifying only line indents and leaving
   * all other whitespace intact.
   *
   * @param file          the file to reformat.
   * @param rangeToAdjust the range of text in which indents should be reformatted.
   * @throws IncorrectOperationException if the file is read-only.
   */
  public abstract void adjustLineIndent(@NotNull PsiFile file, TextRange rangeToAdjust) throws IncorrectOperationException;

  /**
   * Reformats the line at the specified offset in the specified file, modifying only the line indent
   * and leaving all other whitespace intact.
   *
   * @param file   the file to reformat.
   * @param offset the offset the line at which should be reformatted.
   * @throws IncorrectOperationException if the file is read-only.
   */
  public abstract int adjustLineIndent(@NotNull PsiFile file, int offset) throws IncorrectOperationException;

  /**
   * Reformats the line at the specified offset in the specified file, modifying only the line indent
   * and leaving all other whitespace intact.
   *
   * @param document   the document to reformat.
   * @param offset the offset the line at which should be reformatted.
   * @throws IncorrectOperationException if the file is read-only.
   */
  public abstract int adjustLineIndent(@NotNull Document document, int offset);

  /**
   * @deprecated this method is not intended to be used by plugins.
   */
  public abstract boolean isLineToBeIndented(@NotNull PsiFile file, int offset);

  /**
   * Calculates the indent that should be used for the specified line in
   * the specified file.
   *
   * @param file   the file for which the indent should be calculated.
   * @param offset the offset for the line at which the indent should be calculated.
   * @return the indent string (containing of tabs and/or whitespaces), or null if it
   *         was not possible to calculate the indent.
   */
  @Nullable
  public abstract String getLineIndent(@NotNull PsiFile file, int offset);

  /**
   * Calculates the indent that should be used for the current line in the specified
   * editor.
   *
   * @param editor the editor for which the indent should be calculated.
   * @return the indent string (containing of tabs and/or whitespaces), or null if it
   *         was not possible to calculate the indent.
   */
  @Nullable
  public abstract String getLineIndent(@NotNull Editor editor);

  /**
   * @deprecated
   */
  public abstract Indent getIndent(String text, FileType fileType);

  /**
   * @deprecated
   */
  public abstract String fillIndent(Indent indent, FileType fileType);

  /**
   * @deprecated
   */
  public abstract Indent zeroIndent();

  /**
   * @deprecated this method is not designed to be used by plugins.
   */
  @Nullable
  public abstract PsiElement insertNewLineIndentMarker(@NotNull PsiFile file, int offset) throws IncorrectOperationException;

  /**
   * Returns the kind of the specified variable (local, parameter, field, static field or static
   * final field).
   *
   * @param variable the variable to get the kind for.
   * @return the variable kind.
   */
  public abstract VariableKind getVariableKind(@NotNull PsiVariable variable);

  /**
   * Suggests a name for a variable of the specified kind, depending on the code style and
   * the intended use of the variable.
   * @param kind         the kind of the variable.
   * @param propertyName the base name (without code style prefixes) for the variable, or null
   *                     if the base name is not known.
   * @param expr         the expression which will be assigned to the variable, or null if unknown
   * @param type         the expected type of the variable, or null if unknown
   * @return the array of name suggested by the variable, in the order that should be displayed to the user.
   */
  public abstract SuggestedNameInfo suggestVariableName(@NotNull VariableKind kind,
                                                        @Nullable String propertyName,
                                                        @Nullable PsiExpression expr,
                                                        @Nullable PsiType type);

  /**
   * Generates a stripped-down name (with no code style defined prefixes or suffixes, usable as
   * a property name) from the specified name of a variable of the specified kind.
   *
   * @param name         the name of the variable.
   * @param variableKind the kind of the variable.
   * @return the stipped-down name.
   */
  public abstract String variableNameToPropertyName(@NonNls String name, VariableKind variableKind);

  /**
   * Appends code style defined prefixes and/or suffixes for the specified variable kind
   * to the specified variable name.
   *
   * @param propertyName the base name of the variable.
   * @param variableKind the kind of the variable.
   * @return the variable name.
   */
  public abstract String propertyNameToVariableName(@NonNls String propertyName, VariableKind variableKind);

  /**
   * Suggests a unique name for the variable used at the specified location.
   *
   * @param baseName    the base name for the variable.
   * @param place       the location where the variable will be used.
   * @param lookForward if true, the existing variables are searched in both directions; if false - only backward
   * @return the generated unique name,
   */
  public abstract String suggestUniqueVariableName(@NonNls String baseName, PsiElement place, boolean lookForward);

  /**
   * Suggests a unique name for the variable used at the specified location.
   *
   * @param baseNameInfo    the base name info for the variable.
   * @param place       the location where the variable will be used.
   * @param lookForward if true, the existing variables are searched in both directions; if false - only backward
   * @return the generated unique name,
   */
  @NotNull public abstract SuggestedNameInfo suggestUniqueVariableName(@NotNull SuggestedNameInfo baseNameInfo, PsiElement place, boolean lookForward);

  /**
   * Replaces all references to Java classes in the contents of the specified element,
   * except for references to classes in the same package or in implicitly imported packages,
   * with full-qualified references.
   *
   * @param element the element to replace the references in.
   * @return the element in the PSI tree after the qualify operation corresponding to the
   *         original element.
   */
  public abstract PsiElement qualifyClassReferences(@NotNull PsiElement element);

  /**
   * Removes unused import statements from the specified Java file.
   *
   * @param file the file to remove the import statements from.
   * @throws IncorrectOperationException if the operation fails for some reason (for example,
   *                                     the file is read-only).
   */
  public abstract void removeRedundantImports(@NotNull PsiJavaFile file) throws IncorrectOperationException;

  /**
   * Reformats line indents inside new element and reformats white spaces around it
   * @param block - added element parent
   * @param addedElement - new element
   * @throws IncorrectOperationException if the operation fails for some reason (for example,
   *                                     the file is read-only).
   */
  public abstract void reformatNewlyAddedElement(@NotNull final ASTNode block, @NotNull final ASTNode addedElement) throws IncorrectOperationException;
}
