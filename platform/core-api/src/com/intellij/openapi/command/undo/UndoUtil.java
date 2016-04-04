/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.command.undo;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class UndoUtil {
  private UndoUtil() {
  }

  /**
   * make undoable action in current document in order to Undo action work from current file
   *
   * @param file to make editors of to respond to undo action.
   */
  public static void markPsiFileForUndo(@NotNull final PsiFile file) {
    Project project = file.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return;
    CommandProcessor.getInstance().addAffectedDocuments(project, document);
  }

  /**
   * @deprecated please use CommandProcessor.getInstance().addAffectedFiles instead
   */
  public static void markVirtualFileForUndo(@NotNull Project project, @NotNull VirtualFile file) {
    CommandProcessor.getInstance().addAffectedFiles(project, file);
  }

  public static void disableUndoFor(@NotNull Document document) {
    document.putUserData(UndoConstants.DONT_RECORD_UNDO, Boolean.TRUE);
  }

  public static boolean isUndoDisabledFor(@NotNull Document document) {
    return Boolean.TRUE.equals(document.getUserData(UndoConstants.DONT_RECORD_UNDO));
  }
}
