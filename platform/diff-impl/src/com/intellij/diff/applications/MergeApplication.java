// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.applications;

import com.intellij.diff.DiffManagerEx;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.merge.MergeResult;
import com.intellij.ide.CliResult;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

class MergeApplication extends DiffApplicationBase {
  MergeApplication() {
    super("merge", 3, 4);
  }

  @NotNull
  @Override
  public String getUsageMessage() {
    final String scriptName = ApplicationNamesInfo.getInstance().getScriptName();
    return DiffBundle.message("merge.application.usage.parameters.and.description", scriptName);
  }

  @NotNull
  @Override
  public Future<CliResult> processCommand(@NotNull List<String> args, @Nullable String currentDirectory) throws Exception {
    List<String> filePaths = args.subList(1, args.size());
    List<VirtualFile> files = findFiles(filePaths, currentDirectory);
    Project project = guessProject(files);

    List<VirtualFile> contents = Arrays.asList(files.get(0), files.get(2), files.get(1)); // left, base, right
    VirtualFile outputFile = files.get(files.size() - 1);

    if (outputFile == null) throw new Exception("Can't find output file: " + ContainerUtil.getLastItem(filePaths));
    contents = replaceNullsWithEmptyFile(contents);

    AtomicReference<MergeResult> resultRef = new AtomicReference<>();
    MergeRequest request = DiffRequestFactory.getInstance().createMergeRequestFromFiles(project, outputFile, contents,
                                                                                        result -> resultRef.set(result));

    DiffManagerEx.getInstance().showMergeBuiltin(project, request);

    Document document = FileDocumentManager.getInstance().getCachedDocument(outputFile);
    if (document != null) FileDocumentManager.getInstance().saveDocument(document);

    return CompletableFuture.completedFuture(new CliResult(resultRef.get() != MergeResult.CANCEL ? 0 : 1, null));
  }
}
