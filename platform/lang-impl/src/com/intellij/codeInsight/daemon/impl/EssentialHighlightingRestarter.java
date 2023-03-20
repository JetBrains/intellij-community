// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;


import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.SaveAndSyncHandlerListener;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.PsiManagerEx;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Tells {@link DaemonCodeAnalyzerImpl} to run full set of passes after "Save all" to show all diagnostics
 * if the current selected file configured as "Highlight: Essential only"
 */
public class EssentialHighlightingRestarter implements SaveAndSyncHandlerListener {
  private final Project myProject;

  public EssentialHighlightingRestarter(Project project) {
    myProject = project;
  }

  @Override
  public void beforeSave(@NotNull SaveAndSyncHandler.SaveTask task, boolean forceExecuteImmediately) {
    boolean hasFilesWithEssentialHighlightingConfigured =
      Arrays.stream(FileEditorManager.getInstance(myProject).getOpenFiles())
        .map(vf -> ReadAction.compute(() -> PsiManagerEx.getInstanceEx(myProject).getFileManager().findFile(vf)))
        .filter(Objects::nonNull)
        .map(psiFile -> ReadAction.compute(() -> HighlightingSettingsPerFile.getInstance(myProject).getHighlightingSettingForRoot(psiFile) ==
                        FileHighlightingSetting.ESSENTIAL))
        .findAny().isPresent();
    if (hasFilesWithEssentialHighlightingConfigured) {
      DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
      codeAnalyzer.restartToCompleteEssentialHighlighting();
    }
  }
}
