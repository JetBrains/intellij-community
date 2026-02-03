// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.*;
import com.intellij.lang.LanguageFormatting;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Comparator;
import java.util.List;

final class DumpFormattingModelAction extends AnAction implements DumbAware {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null &&
                                             e.getData(CommonDataKeys.PSI_FILE) != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    Project project = e.getProject();
    if (project == null || psiFile == null) return;

    StringBuilder output = new StringBuilder();

    dumpModelForFile(psiFile, output, psiFile.getTextRange());

    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(project);
    List<PsiFile> injectedFiles = injectedLanguageManager
      .getCachedInjectedDocumentsInRange(psiFile, psiFile.getTextRange())
      .stream()
      .map(documentWindow -> PsiDocumentManager.getInstance(project).getPsiFile(documentWindow))
      .filter(psi -> psi != null)
      .sorted(Comparator.comparingInt(psi -> injectedLanguageManager.injectedToHost(psi, psi.getTextRange()).getStartOffset()))
      .toList();

    for (PsiFile injectedFile : injectedFiles) {
      dumpModelForFile(injectedFile, output, injectedLanguageManager.injectedToHost(injectedFile, injectedFile.getTextRange()));
    }

    VirtualFile file;
    try {
      File tempFile = FileUtil.createTempFile("formattingModel-" + psiFile.getName() + "-", ".txt");
      FileUtil.writeToFile(tempFile, output.toString());
      file = LocalFileSystem.getInstance().findFileByIoFile(tempFile);
      if (file == null) {
        return;
      }
    }
    catch (Exception exception) {
      Logger.getInstance(DumpFormattingModelAction.class).error(exception);
      return;
    }

    OpenFileDescriptor fileDescriptor = new OpenFileDescriptor(project, file);
    FileEditorManager.getInstance(project).openEditor(fileDescriptor, true);
  }


  private static void dumpModelForFile(@NotNull PsiFile psiFile, @NotNull StringBuilder output, @NotNull TextRange hostTextRange) {
    FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(psiFile);
    if (builder == null) {
      Logger.getInstance(DumpFormattingModelAction.class).warn("no formatting model found for file: " + psiFile.getName());
      return;
    }

    output.append("> ");
    output.append(hostTextRange);
    output.append("\n");
    FormattingModel model = CoreFormatterUtil.buildModel(builder, psiFile, CodeStyle.getSettings(psiFile), FormattingMode.REFORMAT);
    FormattingModelDumper.dumpFormattingModel(model.getRootBlock(), 0, output);
    output.append("\n\n");
  }
}
