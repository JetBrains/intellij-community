// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.LanguageImportStatements;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;

public final class OptimizeImportsAction extends AnAction {
  private static final @NonNls String HELP_ID = "editing.manageImports";
  private static boolean myProcessVcsChangedFilesInTests;

  public OptimizeImportsAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    actionPerformedImpl(event.getDataContext());
  }

  public static void actionPerformedImpl(@NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final Editor editor = BaseCodeInsightAction.getInjectedEditor(project, CommonDataKeys.EDITOR.getData(dataContext));

    final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);

    PsiFile file = null;
    PsiDirectory dir;

    if (editor != null) {
      file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null) return;
      dir = file.getContainingDirectory();
    }
    else if (files != null && ReformatCodeAction.containsOnlyFiles(files)) {
      new OptimizeImportsProcessor(project, ReformatCodeAction.convertToPsiFiles(files, project), null).run();
      return;
    }
    else {
      Project projectContext = PlatformCoreDataKeys.PROJECT_CONTEXT.getData(dataContext);
      Module moduleContext = LangDataKeys.MODULE_CONTEXT.getData(dataContext);

      if (projectContext != null || moduleContext != null) {
        final String text;
        final boolean hasChanges;
        if (moduleContext != null) {
          text = CodeInsightBundle.message("process.scope.module", moduleContext.getName());
          hasChanges = VcsFacade.getInstance().hasChanges(moduleContext);
        }
        else {
          text = CodeInsightBundle.message("process.scope.project", projectContext.getPresentableUrl());
          hasChanges = VcsFacade.getInstance().hasChanges(projectContext);
        }
        Boolean isProcessVcsChangedText = isProcessVcsChangedText(project, text, hasChanges);
        if (isProcessVcsChangedText == null) {
          return;
        }
        if (moduleContext != null) {
          OptimizeImportsProcessor processor = new OptimizeImportsProcessor(project, moduleContext);
          processor.setProcessChangedTextOnly(isProcessVcsChangedText);
          processor.run();
        }
        else {
          new OptimizeImportsProcessor(projectContext).run();
        }
        return;
      }

      PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
      if (element == null) return;
      if (element instanceof PsiDirectoryContainer) {
        dir = ArrayUtil.getFirstElement(((PsiDirectoryContainer)element).getDirectories());
      }
      else if (element instanceof PsiDirectory) {
        dir = (PsiDirectory)element;
      }
      else {
        file = element.getContainingFile();
        if (file == null) return;
        dir = file.getContainingDirectory();
      }
    }

    boolean processDirectory = false;
    boolean processOnlyVcsChangedFiles = false;
    if (!ApplicationManager.getApplication().isUnitTestMode() && file == null && dir != null) {
      String message = CodeInsightBundle.message("process.scope.directory", dir.getName());
      OptimizeImportsDialog dialog = new OptimizeImportsDialog(project, message, VcsFacade.getInstance().hasChanges(dir));
      dialog.show();
      if (!dialog.isOK()) {
        return;
      }
      processDirectory = true;
      processOnlyVcsChangedFiles = dialog.isProcessOnlyVcsChangedFiles();
    }

    if (processDirectory) {
      new OptimizeImportsProcessor(project, dir, true, processOnlyVcsChangedFiles).run();
    }
    else {
      OptimizeImportsProcessor optimizer = new OptimizeImportsProcessor(project, file);
      if (editor != null && EditorSettingsExternalizable.getInstance().isShowNotificationAfterOptimizeImports()) {
        optimizer.setCollectInfo(true);
        optimizer.setPostRunnable(() -> {
          LayoutCodeInfoCollector collector = optimizer.getInfoCollector();
          if (collector != null) {
            String info = collector.getOptimizeImportsNotification();
            if (!editor.isDisposed() && UIUtil.isShowing(editor.getContentComponent())) {
              String message = info != null ? info : CodeInsightBundle.message("hint.text.no.unused.imports.found");
              FileInEditorProcessor.showHint(editor, message, null);
            }
          }
        });
      }
      optimizer.run();
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    if (!LanguageImportStatements.INSTANCE.hasAnyExtensions()) {
      event.getPresentation().setVisible(false);
      return;
    }

    Presentation presentation = event.getPresentation();
    boolean available = isActionAvailable(event);
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      presentation.setEnabledAndVisible(available);
    }
    else {
      presentation.setEnabled(available);
    }
  }

  private static boolean isActionAvailable(@NotNull AnActionEvent event) {
    DataContext dataContext = event.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return false;
    }

    final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);

    final Editor editor = BaseCodeInsightAction.getInjectedEditor(project, CommonDataKeys.EDITOR.getData(dataContext), false);
    if (editor != null) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null || !isOptimizeImportsAvailable(file)) {
        return false;
      }
    }
    else if (files != null && ReformatCodeAction.containsOnlyFiles(files)) {
      boolean anyHasOptimizeImports = false;
      for (VirtualFile virtualFile : files) {
        PsiFile file = PsiManager.getInstance(project).findFile(virtualFile);
        if (file == null) {
          return false;
        }
        if (isOptimizeImportsAvailable(file)) {
          anyHasOptimizeImports = true;
          break;
        }
      }
      if (!anyHasOptimizeImports) {
        return false;
      }
    }
    else if (files != null && files.length == 1) {
      // skip. Both directories and single files are supported.
    }
    else if (LangDataKeys.MODULE_CONTEXT.getData(dataContext) == null &&
             PlatformCoreDataKeys.PROJECT_CONTEXT.getData(dataContext) == null) {
      PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
      if (element == null) {
        return false;
      }

      if (!(element instanceof PsiDirectory)) {
        PsiFile file = element.getContainingFile();
        if (file == null || !isOptimizeImportsAvailable(file)) {
          return false;
        }
      }
    }

    return true;
  }

  private static boolean isOptimizeImportsAvailable(@NotNull PsiFile file) {
    return !OptimizeImportsProcessor.collectOptimizers(file).isEmpty();
  }

  private static Boolean isProcessVcsChangedText(Project project, @NlsContexts.Label String text, boolean hasChanges) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myProcessVcsChangedFilesInTests;
    }

    OptimizeImportsDialog dialog = new OptimizeImportsDialog(project, text, hasChanges);
    if (!dialog.showAndGet()) {
      return null;
    }

    return dialog.isProcessOnlyVcsChangedFiles();
  }

  @TestOnly
  static void setProcessVcsChangedFilesInTests(boolean value) {
    myProcessVcsChangedFilesInTests = value;
  }

  private static final class OptimizeImportsDialog extends DialogWrapper {
    private final boolean myContextHasChanges;

    private final @NlsContexts.Label String myText;
    private JCheckBox myOnlyVcsCheckBox;
    private final LastRunReformatCodeOptionsProvider myLastRunOptions;

    OptimizeImportsDialog(Project project, @NlsContexts.Label String text, boolean hasChanges) {
      super(project, false);
      myText = text;
      myContextHasChanges = hasChanges;
      myLastRunOptions = new LastRunReformatCodeOptionsProvider(PropertiesComponent.getInstance());
      setOKButtonText(CodeInsightBundle.message("reformat.code.accept.button.text"));
      setTitle(CodeInsightBundle.message("process.optimize.imports"));
      init();
    }

    boolean isProcessOnlyVcsChangedFiles() {
      return myOnlyVcsCheckBox.isSelected();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
      myOnlyVcsCheckBox = new JCheckBox(CodeInsightBundle.message("process.scope.changed.files"));
      myOnlyVcsCheckBox.setEnabled(myContextHasChanges);
      boolean lastRunVcsChangedTextEnabled = myLastRunOptions.getLastTextRangeType() == TextRangeType.VCS_CHANGED_TEXT;
      myOnlyVcsCheckBox.setSelected(myContextHasChanges && lastRunVcsChangedTextEnabled);

      return new FormBuilder()
        .addComponent(new JLabel(myText))
        .setVerticalGap(UIUtil.LARGE_VGAP)
        .addComponent(myOnlyVcsCheckBox)
        .getPanel();
    }

    @Override
    protected String getHelpId() {
      return HELP_ID;
    }
  }
}