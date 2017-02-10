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
import com.intellij.reference.SoftReference;
import com.intellij.ui.LightweightHint;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.ref.WeakReference;

public class ShowContainerInfoHandler implements CodeInsightActionHandler {
  private static final Key<WeakReference<LightweightHint>> MY_LAST_HINT_KEY = Key.create("MY_LAST_HINT_KEY");
  private static final Key<PsiElement> CONTAINER_KEY = Key.create("CONTAINER_KEY");

  @Override
  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement container = null;
    WeakReference<LightweightHint> ref = editor.getUserData(MY_LAST_HINT_KEY);
    LightweightHint hint = SoftReference.dereference(ref);
    if (hint != null && hint.isVisible()){
      hint.hide();
      container = hint.getUserData(CONTAINER_KEY);
      if (container != null && !container.isValid()){
        container = null;
      }
    }

    StructureViewBuilder builder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(file);
    if (builder instanceof TreeBasedStructureViewBuilder) {
      StructureViewModel model = ((TreeBasedStructureViewBuilder) builder).createStructureViewModel(editor);
      boolean goOneLevelUp = true;
      try {
        if (container == null) {
          goOneLevelUp = false;
          Object element = model.getCurrentEditorElement();
          if (element instanceof PsiElement) {
            container = (PsiElement) element;
          }
        }
      }
      finally {
        model.dispose();
      }
      while(true) {
        if (container == null || container instanceof PsiFile) {
          return;
        }
        if (goOneLevelUp) {
          goOneLevelUp = false;
        }
        else {
          if (!isDeclarationVisible(container, editor)) {
            break;
          }
        }

        container = container.getParent();
        while(container != null && DeclarationRangeUtil.getPossibleDeclarationAtRange(container) == null) {
          container = container.getParent();
          if (container instanceof PsiFile) return;
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
    final PsiElement _container = container;
    ApplicationManager.getApplication().invokeLater(() -> {
      LightweightHint hint1 = EditorFragmentComponent.showEditorFragmentHint(editor, range, true, true);
      if (hint1 != null) {
        hint1.putUserData(CONTAINER_KEY, _container);
        editor.putUserData(MY_LAST_HINT_KEY, new WeakReference<>(hint1));
      }
    });
  }

  @Override
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
