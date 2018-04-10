// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Handler, extending IDE behaviour on typing in editor.
 * <p>
 * Note that {@code PsiFile} passed to handler's methods isn't guaranteed to be in sync with the document at the time of invocation
 * (due to performance considerations). {@link com.intellij.psi.PsiDocumentManager#commitDocument(Document)} should be invoked explicitly,
 * if an up-to-date PSI is required.
 *
 * @author yole
 */
public abstract class TypedHandlerDelegate {
  public static final ExtensionPointName<TypedHandlerDelegate> EP_NAME = ExtensionPointName.create("com.intellij.typedHandler");

  /**
   * If the specified character triggers auto-popup, schedules the auto-popup appearance. This method is called even
   * in overwrite mode, when the rest of typed handler delegate methods are not called. It is invoked only for the primary caret.
   */
  @NotNull
  public Result checkAutoPopup(char charTyped, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return Result.CONTINUE;
  }

  /**
   * Called before selected text is deleted.
   * This method is supposed to be overridden by handlers having custom behaviour with respect to selection.
   */
  @NotNull
  public Result beforeSelectionRemoved(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return Result.CONTINUE;
  }

  /**
   * Called before the specified character typed by the user is inserted in the editor.
   */
  @NotNull 
  public Result beforeCharTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, @NotNull FileType fileType) {
    return Result.CONTINUE;
  }

  /**
   * Called after the specified character typed by the user has been inserted in the editor.
   */
  @NotNull                        
  public Result charTyped(char c, @NotNull Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    return Result.CONTINUE;
  }

  public enum Result {
    STOP,
    CONTINUE,
    DEFAULT
  }
}
