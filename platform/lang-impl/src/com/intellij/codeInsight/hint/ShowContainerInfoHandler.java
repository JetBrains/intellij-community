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
package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.ref.WeakReference;

public class ShowContainerInfoHandler implements CodeInsightActionHandler {
  private static final Key<WeakReference<LightweightHint>> MY_LAST_HINT_KEY = Key.create("MY_LAST_HINT_KEY");

  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();


    WeakReference<LightweightHint> ref = editor.getUserData(MY_LAST_HINT_KEY);
    if (ref != null){
      LightweightHint hint = ref.get();
      if (hint != null && hint.isVisible()){
        hint.hide();
      }
    }

    PsiElement container = null;
      StructureViewBuilder builder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(file);
      if (builder instanceof TreeBasedStructureViewBuilder) {
        StructureViewModel model = ((TreeBasedStructureViewBuilder) builder).createStructureViewModel();
        Object element = model.getCurrentEditorElement();
        if (element instanceof PsiElement) {
          container = (PsiElement) element;
          while(true) {
            if (container == null || container instanceof PsiFile) {
              return;
            }
            if (!isDeclarationVisible(container, editor)) {
              break;
            }

            container = container.getParent();
            while(container != null && DeclarationRangeUtil.getPossibleDeclarationAtRange(container) == null) {
              container = container.getParent();
              if (container instanceof PsiFile) return;
            }
          }
        }
      }
      if (container == null) {
        return;
      }

    final TextRange range = DeclarationRangeUtil.getPossibleDeclarationAtRange(container);
    if (range == null) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          LightweightHint hint = EditorFragmentComponent.showEditorFragmentHint(editor, range, true, true);
          if (hint != null) {
            editor.putUserData(MY_LAST_HINT_KEY, new WeakReference<LightweightHint>(hint));
          }
        }
      });
  }

  public boolean startInWriteAction() {
    return false;
  }

  private static boolean isDeclarationVisible(PsiElement container, Editor editor) {
    Rectangle viewRect = editor.getScrollingModel().getVisibleArea();
    final TextRange range = DeclarationRangeUtil.getPossibleDeclarationAtRange(container);
    if (range == null) {
      return false;
    }

    LogicalPosition pos = editor.offsetToLogicalPosition(range.getStartOffset());
    Point loc = editor.logicalPositionToXY(pos);
    return loc.y >= viewRect.y;
  }
}
