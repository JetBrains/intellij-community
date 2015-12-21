/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.applications;

import com.intellij.diff.DiffManagerEx;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.merge.MergeRequest;
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

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public class MergeApplication extends ApplicationStarterBase {
  @Override
  protected boolean checkArguments(@NotNull String[] args) {
    return (args.length == 4 || args.length == 5) && "merge".equals(args[0]);
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
  public void processCommand(@NotNull String[] args, @Nullable String currentDirectory) throws Exception {
    Project project = getProject();

    List<String> filePaths = Arrays.asList(args).subList(1, args.length);
    List<VirtualFile> files = findFiles(filePaths, currentDirectory);

    List<VirtualFile> contents = ContainerUtil.list(files.get(0), files.get(2), files.get(1)); // left, base, right
    VirtualFile outputFile = files.get(files.size() - 1);

    MergeRequest request = DiffRequestFactory.getInstance().createMergeRequestFromFiles(project, outputFile, contents, null);

    DiffManagerEx.getInstance().showMergeBuiltin(project, request);

    Document document = FileDocumentManager.getInstance().getCachedDocument(outputFile);
    if (document != null) FileDocumentManager.getInstance().saveDocument(document);
  }
}
