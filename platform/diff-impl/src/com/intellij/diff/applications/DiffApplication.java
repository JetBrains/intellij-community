// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.applications;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManagerEx;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.chains.SimpleDiffRequestChain;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.util.DiffPlaces;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.ide.CliResult;
import com.intellij.idea.SplashManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

final class DiffApplication extends DiffApplicationBase {
  DiffApplication() {
    super(2, 3);
  }

  @Override
  public String getCommandName() {
    return "diff";
  }

  @NotNull
  @Override
  public String getUsageMessage() {
    final String scriptName = ApplicationNamesInfo.getInstance().getScriptName();
    return DiffBundle.message("diff.application.usage.parameters.and.description", scriptName);
  }

  @Override
  public int getRequiredModality() {
    return NOT_IN_EDT;
  }

  @NotNull
  @Override
  public Future<CliResult> processCommand(@NotNull List<String> args, @Nullable String currentDirectory) throws Exception {
    List<String> filePaths = args.subList(1, args.size());
    List<VirtualFile> files = findFilesOrThrow(filePaths, currentDirectory);
    Project project = guessProject(files);

    CompletableFuture<CliResult> future = new CompletableFuture<>();
    ApplicationManager.getApplication().invokeLater(() -> {
      SimpleDiffRequestChain chain = SimpleDiffRequestChain.fromProducer(new MyDiffRequestProducer(project, files));
      chain.putUserData(DiffUserDataKeys.PLACE, DiffPlaces.EXTERNAL);

      WindowWrapper.Mode mode = project != null ? WindowWrapper.Mode.FRAME : WindowWrapper.Mode.MODAL;
      DiffDialogHints dialogHints = new DiffDialogHints(mode, null, wrapper -> {
        Window window = wrapper.getWindow();
        SplashManager.hideBeforeShow(window);

        UIUtil.runWhenWindowClosed(window, () -> {
          try {
            for (VirtualFile file : files) {
              saveIfNeeded(file);
            }
          }
          finally {
            future.complete(CliResult.OK);
          }
        });
      });

      DiffManagerEx.getInstance().showDiffBuiltin(project, chain, dialogHints);
    });
    return future;
  }

  private static final class MyDiffRequestProducer implements DiffRequestProducer {
    private final Project myProject;
    private final List<VirtualFile> myFiles;

    private MyDiffRequestProducer(@Nullable Project project, @NotNull List<VirtualFile> files) {
      myProject = project;
      myFiles = files;
    }

    @Override
    public @NotNull String getName() {
      if (myFiles.size() == 3) {
        VirtualFile base = myFiles.get(2);
        if (base == null) return DiffBundle.message("diff.files.dialog.title");
        return DiffRequestFactory.getInstance().getTitle(base);
      }
      else {
        return DiffRequestFactory.getInstance().getTitle(myFiles.get(0), myFiles.get(1));
      }
    }

    @Override
    public @NotNull DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
      throws DiffRequestProducerException, ProcessCanceledException {
      if (myFiles.size() == 3) {
        List<VirtualFile> nonNullFiles = replaceNullsWithEmptyFile(myFiles);
        return DiffRequestFactory.getInstance().createFromFiles(myProject, nonNullFiles.get(0), nonNullFiles.get(2), nonNullFiles.get(1));
      }
      else {
        return DiffRequestFactory.getInstance().createFromFiles(myProject, myFiles.get(0), myFiles.get(1));
      }
    }
  }
}
