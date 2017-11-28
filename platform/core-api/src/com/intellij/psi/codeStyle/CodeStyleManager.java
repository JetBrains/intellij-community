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
package com.intellij.psi.codeStyle;

import com.intellij.formatting.FormattingMode;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Service for reformatting code fragments, getting names for elements
 * according to the user's code style and working with import statements and full-qualified names.
 */
public abstract class CodeStyleManager  {
  /**
   * Returns the code style manager for the specified project.
   *
   * @param project the project to get the code style manager for.
   * @return the code style manager instance.
   */
  public static CodeStyleManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, CodeStyleManager.class);
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
   * @see #reformatText(PsiFile, int, int)
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
   * @see #reformatText(PsiFile, int, int)
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
   * @see #reformatText(PsiFile, int, int)
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
   * @see #reformatText(PsiFile, int, int)
   */
  public abstract PsiElement reformatRange(@NotNull PsiElement element,
                                           int startOffset,
                                           int endOffset,
                                           boolean canChangeWhiteSpacesOnly) throws IncorrectOperationException;

  /**
   * Delegates to the {@link #reformatText(PsiFile, Collection)} with the single range defined by the given offsets.
   *
   * @param file     the file to reformat.
   * @param startOffset the start of the text range to reformat.
   * @param endOffset   the end of the text range to reformat.
   * @throws IncorrectOperationException if the file to reformat is read-only.
   */
  public abstract void reformatText(@NotNull PsiFile file, int startOffset, int endOffset) throws IncorrectOperationException;

  /**
   * Re-formats a ranges of text in the specified file. This method works faster than
   * {@link #reformatRange(PsiElement, int, int)} but invalidates the
   * PSI structure for the file.
   *
   * @param file  the file to reformat
   * @param ranges   ranges to process
   * @throws IncorrectOperationException  if the file to reformat is read-only.
   */
  public abstract void reformatText(@NotNull PsiFile file, @NotNull Collection<TextRange> ranges) throws IncorrectOperationException;

  public abstract void reformatTextWithContext(@NotNull PsiFile file, @NotNull ChangedRangesInfo info) throws IncorrectOperationException;
  
  public void reformatTextWithContext(@NotNull PsiFile file, @NotNull Collection<TextRange> ranges) throws IncorrectOperationException {
    List<TextRange> rangesList = ContainerUtil.newArrayList(ranges);
    reformatTextWithContext(file, new ChangedRangesInfo(rangesList, null));
  }
  
  /**
   * Re-formats the specified range of a file, modifying only line indents and leaving
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
   * @param document for which the indent should be calculated.
   * @return the indent string (containing of tabs and/or whitespaces), or null if it
   *         was not possible to calculate the indent.
   */
  @Nullable
  public abstract String getLineIndent(@NotNull Document document, int offset);

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
   * Reformats line indents inside new element and reformats white spaces around it
   * @param block - added element parent
   * @param addedElement - new element
   * @throws IncorrectOperationException if the operation fails for some reason (for example,
   *                                     the file is read-only).
   */
  public abstract void reformatNewlyAddedElement(@NotNull final ASTNode block, @NotNull final ASTNode addedElement) throws IncorrectOperationException;

  /**
   * Formatting may be executed sequentially, i.e. the whole (re)formatting task is split into a number of smaller sub-tasks
   * that are executed sequentially. That is done primarily for ability to show progress dialog during formatting (formatting
   * is always performed from EDT, hence, the GUI freezes if we perform formatting as a single big iteration).
   * <p/>
   * However, there are situation when we don't want to use such an approach - for example, IntelliJ IDEA sometimes inserts dummy
   * text into file in order to calculate formatting-specific data and removes it after that. We don't want to allow Swing events
   * dispatching during that in order to not show that dummy text to the end-user.
   * <p/>
   * It's possible to configure that (implementation details are insignificant here) and current method serves as a read-only
   * facade for obtaining information if 'sequential' processing is allowed at the moment.
   *
   * @return      {@code true} if 'sequential' formatting is allowed now; {@code false} otherwise
   */
  public abstract boolean isSequentialProcessingAllowed();

  /**
   * Disables automatic formatting of modified PSI elements, runs the specified operation
   * and re-enables the formatting. Can be used to improve performance of PSI write
   * operations.
   *
   * @param r the operation to run.
   */
  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  public abstract void performActionWithFormatterDisabled(Runnable r);

  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  public abstract <T extends Throwable> void performActionWithFormatterDisabled(ThrowableRunnable<T> r) throws T;

  public abstract <T> T performActionWithFormatterDisabled(Computable<T> r);

  /**
   * Calculates minimum spacing, allowed by formatting model (in columns) for a block starting at given offset,
   * relative to its previous sibling block.
   * Returns {@code -1}, if required block cannot be found at provided offset,
   * or spacing cannot be calculated due to some other reason.
   */
  public int getSpacing(@NotNull PsiFile file, int offset) {
    return -1;
  }

  /**
   * Calculates minimum number of line feeds that should precede block starting at given offset, as dictated by formatting model.
   * Returns {@code -1}, if required block cannot be found at provided offset,
   * or spacing cannot be calculated due to some other reason.
   */
  public int getMinLineFeeds(@NotNull PsiFile file, int offset) {
    return -1;
  }

  /**
   * Retrieves the current formatting mode.
   * 
   * @param project The current project used to obtain {@code CodeStyleManager} instance.
   * @return The current formatting mode.
   * @see FormattingMode
   */
  public static FormattingMode getCurrentFormattingMode(@NotNull Project project) {
    if (!project.isDisposed()) {
      CodeStyleManager instance = getInstance(project);
      if (instance instanceof FormattingModeAwareIndentAdjuster) {
        return ((FormattingModeAwareIndentAdjuster)instance).getCurrentFormattingMode();
      }
    }
    return FormattingMode.REFORMAT;
  }

  /**
   * Run the given runnable disabling doc comment formatting.
   * @param file     The file for which doc comment formatting should be temporarily disabled.
   * @param runnable The runnable to run.
   */
  public void runWithDocCommentFormattingDisabled(@NotNull PsiFile file, @NotNull Runnable runnable) {
    runnable.run();
  }

  @NotNull
  public DocCommentSettings getDocCommentSettings(@NotNull PsiFile file) {
    return DocCommentSettings.DEFAULTS;
  }
}
