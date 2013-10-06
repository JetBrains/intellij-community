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

import com.intellij.CommonBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class CreateFromTemplateAction<T extends PsiElement> extends AnAction {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.CreateFromTemplateAction");

  public CreateFromTemplateAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  public final void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();

    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) {
      return;
    }

    final Project project = CommonDataKeys.PROJECT.getData(dataContext);

    final PsiDirectory dir = view.getOrChooseDirectory();
    if (dir == null || project == null) return;

    final CreateFileFromTemplateDialog.Builder builder = CreateFileFromTemplateDialog.createDialog(project);
    buildDialog(project, dir, builder);

    final Ref<T> createdElement = Ref.create(null);
    final Ref<PsiFile> createdFile = Ref.create(null);
    final Ref<String> selectedTemplateName = Ref.create(null);
    builder.show(getErrorTitle(), getDefaultTemplateName(dir), new CreateFileFromTemplateDialog.FileCreator<T>() {

        @Override
        public T createFile(@NotNull String name, @NotNull String templateName) {
          if (StringUtil.countChars(name, '.') == 1) {
            FileType fileType = CreateDirectoryOrPackageHandler.findFileTypeBoundToName(name);
            if (fileType != null) {
              String message = "The name you entered looks like a file name. Do you want to create a file named " + name + " instead?";
              int ec = Messages.showYesNoDialog(project, message, 
                                                "File Name Detected", 
                                                "Yes, create " + name, 
                                                "No, create " + e.getPresentation().getText(), 
                                                fileType.getIcon());
              if (ec == Messages.OK) {
                PsiFile newFile = dir.createFile(name);
                createdFile.set(newFile);
                //noinspection unchecked
                return (T)newFile;
              }
            }
          }

          selectedTemplateName.set(templateName);
          T created = CreateFromTemplateAction.this.createFile(name, templateName, dir);
          createdElement.set(created);
          return created;
        }

        @Override
        @NotNull
        public String getActionName(@NotNull String name, @NotNull String templateName) {
          return CreateFromTemplateAction.this.getActionName(dir, name, templateName);
        }
      });
    if (!createdFile.isNull()) {
      view.selectElement(createdFile.get());
    }
    else if (!createdElement.isNull()) {
      view.selectElement(createdElement.get());
      postProcess(createdElement.get(), selectedTemplateName.get(), builder.getCustomProperties());
    }
  }

  protected void postProcess(T createdElement, String templateName, Map<String,String> customProperties) {
  }

  @Nullable
  protected abstract T createFile(String name, String templateName, PsiDirectory dir);

  protected abstract void buildDialog(Project project, PsiDirectory directory, CreateFileFromTemplateDialog.Builder builder);

  @Nullable
  protected String getDefaultTemplateName(@NotNull PsiDirectory dir) {
    String property = getDefaultTemplateProperty();
    return property == null ? null : PropertiesComponent.getInstance(dir.getProject()).getValue(property);
  }

  @Nullable
  protected String getDefaultTemplateProperty() {
    return null;
  }

  @Override
  public void update(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Presentation presentation = e.getPresentation();

    final boolean enabled = isAvailable(dataContext);

    presentation.setVisible(enabled);
    presentation.setEnabled(enabled);
  }

  protected boolean isAvailable(DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    return project != null && view != null && view.getDirectories().length != 0;
  }

  protected abstract String getActionName(PsiDirectory directory, String newName, String templateName);

  protected String getErrorTitle() {
    return CommonBundle.getErrorTitle();
  }

  //todo append $END variable to templates?
  protected static void moveCaretAfterNameIdentifier(PsiNameIdentifierOwner createdElement) {
    final Project project = createdElement.getProject();
    final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor != null) {
      final VirtualFile virtualFile = createdElement.getContainingFile().getVirtualFile();
      if (virtualFile != null) {
        if (FileDocumentManager.getInstance().getDocument(virtualFile) == editor.getDocument()) {
          final PsiElement nameIdentifier = createdElement.getNameIdentifier();
          if (nameIdentifier != null) {
            editor.getCaretModel().moveToOffset(nameIdentifier.getTextRange().getEndOffset());
          }
        }
      }
    }
  }
}
