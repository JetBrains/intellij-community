// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.applications;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManagerEx;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.merge.MergeRequestProducer;
import com.intellij.diff.merge.MergeResult;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

final class MergeApplication extends DiffApplicationBase {
  MergeApplication() {
    super(3, 4);
  }

  @Override
  public String getCommandName() {
    return "merge";
  }

  @NotNull
  @Override
  public String getUsageMessage() {
    final String scriptName = ApplicationNamesInfo.getInstance().getScriptName();
    return DiffBundle.message("merge.application.usage.parameters.and.description", scriptName);
  }

  @Override
  public int getRequiredModality() {
    return NOT_IN_EDT;
  }

  @NotNull
  @Override
  public Future<CliResult> processCommand(@NotNull List<String> args, @Nullable String currentDirectory) throws Exception {
    List<String> filePaths = args.subList(1, 4);
    List<VirtualFile> files = findFilesOrThrow(filePaths, currentDirectory);
    Project project = guessProject(files);

    List<VirtualFile> contents = Arrays.asList(files.get(0), files.get(2), files.get(1)); // left, base, right

    VirtualFile outputFile;
    if (args.size() == 5) {
      String outputFilePath = args.get(4);
      outputFile = findOrCreateFile(outputFilePath, currentDirectory);
      refreshAndEnsureFilesValid(Collections.singletonList(outputFile));
    }
    else {
      outputFile = files.get(2); // base
    }
    if (outputFile == null) throw new Exception(DiffBundle.message("cannot.create.file.error", ContainerUtil.getLastItem(filePaths)));

    CompletableFuture<CliResult> future = new CompletableFuture<>();
    AtomicReference<CliResult> resultRef = new AtomicReference<>(new CliResult(127, null));
    ApplicationManager.getApplication().invokeLater(() -> {
      MergeRequestProducer requestProducer = new MyMergeRequestProducer(project, outputFile, contents, resultRef);

      WindowWrapper.Mode mode = project != null ? WindowWrapper.Mode.FRAME : WindowWrapper.Mode.MODAL;
      DiffDialogHints dialogHints = new DiffDialogHints(mode, null, wrapper -> {
        Window window = wrapper.getWindow();
        SplashManager.hideBeforeShow(window);

        UIUtil.runWhenWindowClosed(window, () -> {
          future.complete(resultRef.get());
        });
      });
      DiffManagerEx.getInstance().showMergeBuiltin(project, requestProducer, dialogHints);
    });
    return future;
  }

  private static final class MyMergeRequestProducer implements MergeRequestProducer {
    private final Project myProject;
    private final VirtualFile myOutputFile;
    private final List<VirtualFile> myContents;
    private final AtomicReference<CliResult> myResultRef;

    private MyMergeRequestProducer(@Nullable Project project,
                                   @NotNull VirtualFile outputFile,
                                   @NotNull List<VirtualFile> contents,
                                   @NotNull AtomicReference<CliResult> resultRef) {
      myProject = project;
      myOutputFile = outputFile;
      myContents = contents;
      myResultRef = resultRef;
    }

    @Override
    public @NotNull String getName() {
      return DiffBundle.message("merge.window.title.file", myOutputFile.getPresentableUrl());
    }

    @Override
    public @NotNull MergeRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
      throws DiffRequestProducerException, ProcessCanceledException {
      try {
        List<VirtualFile> contents = replaceNullsWithEmptyFile(myContents);
        return DiffRequestFactory.getInstance().createMergeRequestFromFiles(myProject, myOutputFile, contents, result -> {
          try {
            saveIfNeeded(myOutputFile);
          }
          finally {
            int exitCode = result != MergeResult.CANCEL ? 0 : 1;
            myResultRef.set(new CliResult(exitCode, null));
          }
        });
      }
      catch (Throwable e) {
        myResultRef.set(new CliResult(127, e.getMessage()));
        throw new DiffRequestProducerException(e);
      }
    }
  }
}
