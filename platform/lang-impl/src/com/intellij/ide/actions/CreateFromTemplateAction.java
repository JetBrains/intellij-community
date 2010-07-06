/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class CreateFromTemplateAction<T extends PsiElement> extends AnAction {
  public CreateFromTemplateAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  public final void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();

    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) {
      return;
    }

    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    final PsiDirectory dir = view.getOrChooseDirectory();
    if (dir == null) return;

    final T createdElement =
      buildDialog(project, dir).show(getErrorTitle(), getDefaultTempalteName(dir), new CreateFileFromTemplateDialog.FileCreator<T>() {
        public void checkBeforeCreate(@NotNull String name, @NotNull String templateName) {
          CreateFromTemplateAction.this.checkBeforeCreate(name, templateName, dir);
        }

        public T createFile(@NotNull String name, @NotNull String templateName) {
          return CreateFromTemplateAction.this.createFile(name, templateName, dir);
        }

        @NotNull
        public String getActionName(@NotNull String name, @NotNull String templateName) {
          return CreateFromTemplateAction.this.getActionName(dir, name, templateName);
        }
      });
    if (createdElement != null) {
      view.selectElement(createdElement);
    }
  }

  @Nullable
  protected abstract T createFile(String name, String templateName, PsiDirectory dir);

  protected abstract void checkBeforeCreate(String name, String templateName, PsiDirectory dir);

  @NotNull
  protected abstract CreateFileFromTemplateDialog.Builder buildDialog(Project project, PsiDirectory directory);

  @Nullable
  protected String getDefaultTempalteName(@NotNull PsiDirectory dir) {
    return null;
  }

  public void update(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Presentation presentation = e.getPresentation();

    final boolean enabled = isAvailable(dataContext);

    presentation.setVisible(enabled);
    presentation.setEnabled(enabled);
  }

  protected boolean isAvailable(DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    return project != null && view != null && view.getDirectories().length != 0;
  }

  protected abstract String getActionName(PsiDirectory directory, String newName, String templateName);

  protected abstract String getErrorTitle();
}
