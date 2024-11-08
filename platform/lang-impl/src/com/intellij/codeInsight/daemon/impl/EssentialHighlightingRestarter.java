// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;


import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.SaveAndSyncHandlerListener;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.impl.PsiManagerEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Tells {@link DaemonCodeAnalyzerImpl} to run full set of passes after "Save all" action was invoked, to show all diagnostics
 * if the current selected file configured as "Highlight: Essential only"
 */
@ApiStatus.Internal
public final class EssentialHighlightingRestarter implements SaveAndSyncHandlerListener {
  private final Project myProject;

  public EssentialHighlightingRestarter(Project project) {
    myProject = project;
  }

  @Override
  public void beforeSave(@NotNull SaveAndSyncHandler.SaveTask task, boolean forceExecuteImmediately) {
    if (!Registry.is("highlighting.essential.should.restart.in.full.mode.on.save.all")) {
      return;
    }
    boolean hasFilesWithEssentialHighlightingConfigured =
      Arrays.stream(FileEditorManager.getInstance(myProject).getOpenFiles())
        .map(vf -> ReadAction.nonBlocking(() -> PsiManagerEx.getInstanceEx(myProject).getFileManager().findFile(vf)).executeSynchronously())
        .filter(Objects::nonNull)
        .anyMatch(psiFile -> ReadAction.nonBlocking(() -> HighlightingSettingsPerFile.getInstance(myProject).getHighlightingSettingForRoot(psiFile) ==
                                                      FileHighlightingSetting.ESSENTIAL).executeSynchronously());
    if (hasFilesWithEssentialHighlightingConfigured) {
      DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
      codeAnalyzer.requestRestartToCompleteEssentialHighlighting();
    }
  }
}
