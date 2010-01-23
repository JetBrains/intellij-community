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

package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lang.LanguageImportStatements;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;

public class OptimizeImportsAction extends AnAction {
  private static final @NonNls String HELP_ID = "editing.manageImports";

  public void actionPerformed(AnActionEvent event) {
    actionPerformedImpl(event.getDataContext());
  }

  public static void actionPerformedImpl(final DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final Editor editor = BaseCodeInsightAction.getInjectedEditor(project, PlatformDataKeys.EDITOR.getData(dataContext));

    final VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);

    PsiFile file = null;
    PsiDirectory dir;

    if (editor != null){
      file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null) return;
      dir = file.getContainingDirectory();
    }
    else if (ReformatCodeAction.areFiles(files)) {
      final ReadonlyStatusHandler.OperationStatus operationStatus = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files);
      if (!operationStatus.hasReadonlyFiles()) {
        new OptimizeImportsProcessor(project, ReformatCodeAction.convertToPsiFiles(files, project), null).run();
      }
      return;
    }
    else{
      Project projectContext = PlatformDataKeys.PROJECT_CONTEXT.getData(dataContext);
      Module moduleContext = LangDataKeys.MODULE_CONTEXT.getData(dataContext);

      if (projectContext != null || moduleContext != null) {
        final String text;
        if (moduleContext != null) {
          text = CodeInsightBundle.message("process.scope.module", moduleContext.getName());
        }
        else {
          text = CodeInsightBundle.message("process.scope.project", projectContext.getPresentableUrl());
        }
        LayoutProjectCodeDialog dialog = new LayoutProjectCodeDialog(project, CodeInsightBundle.message("process.optimize.imports"), text, false);
        dialog.show();
        if (!dialog.isOK()) return;
        if (moduleContext != null) {
          new OptimizeImportsProcessor(project, moduleContext).run();
        }
        else {
          new OptimizeImportsProcessor(projectContext).run();
        }
        return;
      }

      PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
      if (element == null) return;
      if (element instanceof PsiDirectoryContainer) {
        dir = ((PsiDirectoryContainer)element).getDirectories()[0];
      }
      else if (element instanceof PsiDirectory) {
        dir = (PsiDirectory)element;
      }
      else{
        file = element.getContainingFile();
        if (file == null) return;
        dir = file.getContainingDirectory();
      }
    }

    boolean processDirectory;
    boolean includeSubdirectories;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      includeSubdirectories = processDirectory = false;
    }
    else if (!EditorSettingsExternalizable.getInstance().getOptions().SHOW_OPIMIZE_IMPORTS_DIALOG) {
      includeSubdirectories = processDirectory = false;
    }
    else {
      final LayoutCodeDialog dialog = new LayoutCodeDialog(project, CodeInsightBundle.message("process.optimize.imports"), file, dir, null, HELP_ID);
      dialog.show();
      if (!dialog.isOK()) return;
      EditorSettingsExternalizable.getInstance().getOptions().SHOW_OPIMIZE_IMPORTS_DIALOG = !dialog.isDoNotAskMe();
      ReformatCodeAction.updateShowDialogSetting(dialog, "\"Optimize Imports\" dialog disabled");
      processDirectory = dialog.isProcessDirectory();
      includeSubdirectories = dialog.isIncludeSubdirectories();
    }

    if (processDirectory){
      new OptimizeImportsProcessor(project, dir, includeSubdirectories).run();
    }
    else{
      new OptimizeImportsProcessor(project, file).run();
    }
  }

  public void update(AnActionEvent event){
    if (!LanguageImportStatements.INSTANCE.hasAnyExtensions()) {
      event.getPresentation().setVisible(false);
      return;
    }

    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null){
      presentation.setEnabled(false);
      return;
    }

    final VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);

    final Editor editor = BaseCodeInsightAction.getInjectedEditor(project, PlatformDataKeys.EDITOR.getData(dataContext), false);
    if (editor != null){
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null || !isOptimizeImportsAvailable(file)){
        presentation.setEnabled(false);
        return;
      }
    }
    else if (files != null && ReformatCodeAction.areFiles(files)) {
      boolean anyHasOptimizeImports = false;
      for (VirtualFile virtualFile : files) {
        PsiFile file = PsiManager.getInstance(project).findFile(virtualFile);
        if (file == null) {
          presentation.setEnabled(false);
          return;
        }
        if (isOptimizeImportsAvailable(file)) {
          anyHasOptimizeImports = true;
        }
      }
      if (!anyHasOptimizeImports) {
        presentation.setEnabled(false);
        return;
      }
    }
    else if (files != null && files.length == 1) {
      // skip. Both directories and single files are supported.
    }
    else if (LangDataKeys.MODULE_CONTEXT.getData(dataContext) == null &&
             PlatformDataKeys.PROJECT_CONTEXT.getData(dataContext) == null) {
      PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
      if (element == null){
        presentation.setEnabled(false);
        return;
      }

      if (!(element instanceof PsiDirectory)){
        PsiFile file = element.getContainingFile();
        if (file == null || !isOptimizeImportsAvailable(file)){
          presentation.setEnabled(false);
          return;
        }
      }
    }

    presentation.setEnabled(true);
  }

  private static boolean isOptimizeImportsAvailable(final PsiFile file) {
    return LanguageImportStatements.INSTANCE.forFile(file) != null;
  }
}
