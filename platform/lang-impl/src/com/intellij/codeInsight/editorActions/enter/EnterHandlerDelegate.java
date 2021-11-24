// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.editorActions.enter;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This extension allows to modify the behaviour of 'Enter' action in editor (usually bound to 'Enter' key).
 */
public interface EnterHandlerDelegate {
  ExtensionPointName<EnterHandlerDelegate> EP_NAME = ExtensionPointName.create("com.intellij.enterHandlerDelegate");

  /**
   * Value returned from {@link #preprocessEnter(PsiFile, Editor, Ref, Ref, DataContext, EditorActionHandler)} and
   * {@link #postProcessEnter(PsiFile, Editor, DataContext)}. Meaning of specific enum constants is explained in respective methods'
   * documentation.
   */
  enum Result {
    Default, Continue, DefaultForceIndent, DefaultSkipIndent, Stop
  }

  /**
   * Called before the actual Enter processing is done for the caret inside indent space.
   * <b>Important Note: A document associated with the editor may have modifications which are not reflected yet in the PSI file. If any
   * operations with PSI are needed including a search for PSI elements, the document must be committed first to update the PSI.
   * For example:</b>
   * <code><pre>
   *   PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument);
   * </pre></code>
   *
   * @param newLineCharOffset The end offset of the previous line;
   *                          <code>newLineCharOffset < 0</code> for the indent space in the top line of the document.
   * @param editor            The editor.
   * @param dataContext       The data context passed to the Enter handler.
   * @return <code>true</code> if the handler is responsible for Enter processing inside the indent space,
   *         <code>false</code> invokes the default Enter processing procedure inside the indent space.
   */
  default boolean invokeInsideIndent(int newLineCharOffset,
                                     @NotNull Editor editor,
                                     @NotNull final DataContext dataContext) {
    return false;
  }

  /**
   * Called before the actual Enter processing is done.
   * <b>Important Note: A document associated with the editor may have modifications which are not reflected yet in the PSI file. If any
   * operations with PSI are needed including a search for PSI elements, the document must be committed first to update the PSI.
   * For example:</b>
   * <code><pre>
   *   PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument);
   * </pre></code>
   *
   * @param file            The PSI file associated with the document.
   * @param editor          The editor.
   * @param caretOffset     Indicates a place where line break is to be inserted (it's a caret position initially). Method implementation
   *                        can change this value to adjust target line break position.
   * @param caretAdvance    A reference to the number of columns by which the caret must be moved forward.
   * @param dataContext     The data context passed to the enter handler.
   * @param originalHandler The original handler.
   * @return One of <code>{@link Result} values</code>, defining next steps in action processing:
   *         <table>
   *         <tr><td>{@code Default}<td>default 'Enter' logic should be executed, without calling other extensions'
   *                                    {@code preprocessEnter} methods
   *         <tr><td>{@code Continue}<td>processing should proceed normally, i.e. other extensions should be processed,
   *                                     and default 'Enter' logic should be executed
   *         <tr><td>{@code DefaultForceIndent}<td>same as {@code Default}, but also forces the indentation of newly created line in editor
   *                                               (when not forced, this is only performed if 'Smart indent' is enabled in editor settings)
   *         <tr><td>{@code DefaultSkipIndent}<td>same as {@code Default}, but also disables any post-processing after executing basic
   *                                              'Enter' logic (including 'smart' indenting and post-processing defined by extensions)
   *         <tr><td>{@code Stop}<td>aborts action execution, so that no other processing (either default or defined by extensions) is
   *                                 performed
   *         </table>
   */
  Result preprocessEnter(@NotNull final PsiFile file, @NotNull final Editor editor, @NotNull final Ref<Integer> caretOffset,
                         @NotNull final Ref<Integer> caretAdvance, @NotNull final DataContext dataContext,
                         @Nullable final EditorActionHandler originalHandler);

  /**
   * Called at the end of Enter handling after line feed insertion and indentation adjustment.
   * <p>
   * <b>Important Note: A document associated with the editor has modifications which are not reflected yet in the PSI file. If any
   * operations with PSI are needed including a search for PSI elements, the document must be committed first to update the PSI.
   * For example:</b>
   * <code><pre>
   *   PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument);
   * </pre></code>
   *
   * @param file        The PSI file associated with the document.
   * @param editor      The editor.
   * @param dataContext The data context passed to the Enter handler.
   * @return One of <code>{@link Result} values</code>, defining next steps in action processing:
   *         <table>
   *         <tr><td>{@code Stop}<td>forbids post-processing from further extensions
   *         <tr><td>any other value<td>processing should proceed normally, i.e. post-processing other extensions should be executed
   *         </table>
   * @see DataContext
   * @see com.intellij.psi.PsiDocumentManager
   */
  Result postProcessEnter(@NotNull PsiFile file, @NotNull Editor editor, @NotNull DataContext dataContext);
}
