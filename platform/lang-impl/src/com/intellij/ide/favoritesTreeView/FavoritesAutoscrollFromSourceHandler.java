/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.actions.SelectInContextImpl;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.AutoScrollFromSourceHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class FavoritesAutoscrollFromSourceHandler extends AutoScrollFromSourceHandler {
  private final FavoritesViewSelectInTarget mySelectInTarget = new FavoritesViewSelectInTarget(myProject);

  public FavoritesAutoscrollFromSourceHandler(@NotNull Project project, @NotNull FavoritesViewTreeBuilder builder) {
    super(project, builder.getTree(), builder);
  }

  @Override
  protected boolean isAutoScrollEnabled() {
    return FavoritesManager.getInstance(myProject).getViewSettings().isAutoScrollFromSource();
  }

  @Override
  protected void setAutoScrollEnabled(boolean enabled) {
    FavoritesManager.getInstance(myProject).getViewSettings().setAutoScrollFromSource(enabled);
  }

  @Override
  protected void selectElementFromEditor(@NotNull FileEditor editor) {
    final VirtualFile file = FileEditorManagerEx.getInstanceEx(myProject).getFile(editor);
    if (file != null) {
      final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
      if (psiFile != null) {
        final SelectInContext selectInContext = SelectInContextImpl.createEditorContext(myProject, editor);
        if (mySelectInTarget.canSelect(selectInContext)) {
          mySelectInTarget.selectIn(selectInContext, false);
        }
      }
    }
  }
}