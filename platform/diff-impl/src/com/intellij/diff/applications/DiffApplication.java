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
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public class DiffApplication extends ApplicationStarterBase {
  @Override
  protected boolean checkArguments(@NotNull String[] args) {
    return args.length == 3 && "diff".equals(args[0]);
  }

  @Override
  public String getCommandName() {
    return "diff";
  }

  @NotNull
  public String getUsageMessage() {
    final String scriptName = ApplicationNamesInfo.getInstance().getScriptName();
    return DiffBundle.message("diff.application.usage.parameters.and.description", scriptName);
  }

  public void processCommand(@NotNull String[] args, @Nullable String currentDirectory) throws Exception {
    // TODO: try to guess 'right' project ?

    final String path1 = args[1];
    final String path2 = args[2];

    final VirtualFile file1 = findFile(path1, currentDirectory);
    final VirtualFile file2 = findFile(path2, currentDirectory);

    if (file1 == null) throw new Exception("Can't find file " + path1);
    if (file2 == null) throw new Exception("Can't find file " + path2);

    VfsUtil.markDirtyAndRefresh(false, false, false, file1, file2);
    DiffRequest request = DiffRequestFactory.getInstance().createFromFiles(null, file1, file2);

    Project project = DefaultProjectFactory.getInstance().getDefaultProject();
    DiffManagerEx.getInstance().showDiffBuiltin(project, request, DiffDialogHints.MODAL);
  }
}
