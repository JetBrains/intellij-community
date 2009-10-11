/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
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
    final DocumentReference ref = DocumentReferenceManager.getInstance().create(document);
    markDocumentReferenceForUndo(project, ref, "markDocumentForUndo: " + file);
  }

  public static void markVirtualFileForUndo(@NotNull Project project, @NotNull VirtualFile file) {
    final DocumentReference ref = DocumentReferenceManager.getInstance().create(file);
    markDocumentReferenceForUndo(project, ref, "markVirtualFileForUndo: " + file.getPath());
  }

  private static void markDocumentReferenceForUndo(final Project project,
                                                   final DocumentReference ref,
                                                   @NonNls final String debugName) {
    UndoManager.getInstance(project).undoableActionPerformed(new UndoableAction() {
      public void undo() {
      }

      public void redo() {
      }

      public DocumentReference[] getAffectedDocuments() {
        return new DocumentReference[]{ref};
      }

      public boolean isGlobal() {
        return false;
      }

      @NonNls
      public String toString() {
        return debugName;
      }
    });
  }
}