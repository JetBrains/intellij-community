// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.applications;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManagerEx;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.chains.SimpleDiffRequestChain;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.util.DiffPlaces;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.ide.CliResult;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

final class DiffApplication extends DiffApplicationBase {
  DiffApplication() {
    super("diff", 2, 3);
  }

  @NotNull
  @Override
  public String getUsageMessage() {
    final String scriptName = ApplicationNamesInfo.getInstance().getScriptName();
    return DiffBundle.message("diff.application.usage.parameters.and.description", scriptName);
  }

  @NotNull
  @Override
  public Future<CliResult> processCommand(@NotNull List<String> args, @Nullable String currentDirectory) throws Exception {
    List<String> filePaths = args.subList(1, args.size());
    List<VirtualFile> files = findFiles(filePaths, currentDirectory);
    Project project = guessProject(files);

    DiffRequest request;
    if (files.size() == 3) {
      files = replaceNullsWithEmptyFile(files);
      request = DiffRequestFactory.getInstance().createFromFiles(project, files.get(0), files.get(2), files.get(1));
    }
    else {
      request = DiffRequestFactory.getInstance().createFromFiles(project, files.get(0), files.get(1));
    }

    SimpleDiffRequestChain chain = new SimpleDiffRequestChain(request);
    chain.putUserData(DiffUserDataKeys.PLACE, DiffPlaces.EXTERNAL);

    if (project != null) {
      CompletableFuture<CliResult> future = new CompletableFuture<>();
      Runnable resultCallback = () -> future.complete(CliResult.OK);
      DiffDialogHints dialogHints = new DiffDialogHints(
        WindowWrapper.Mode.FRAME, null, wrapper -> UIUtil.runWhenWindowClosed(wrapper.getWindow(), resultCallback));
      DiffManagerEx.getInstance().showDiffBuiltin(project, chain, dialogHints);
      return future;
    }
    else {
      DiffManagerEx.getInstance().showDiffBuiltin(null, chain, DiffDialogHints.MODAL);
      return CliResult.OK_FUTURE;
    }
  }
}
