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

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManagerEx;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.chains.SimpleDiffRequestChain;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.util.DiffPlaces;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public class DiffApplication extends DiffApplicationBase {
  @Override
  protected boolean checkArguments(@NotNull String[] args) {
    return (args.length == 3 || args.length == 4) && "diff".equals(args[0]);
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
  public void processCommand(@NotNull String[] args, @Nullable String currentDirectory) throws Exception {
    List<String> filePaths = Arrays.asList(args).subList(1, args.length);
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

    DiffDialogHints dialogHints = project != null ? DiffDialogHints.DEFAULT : DiffDialogHints.MODAL;
    DiffManagerEx.getInstance().showDiffBuiltin(project, chain, dialogHints);
  }
}
