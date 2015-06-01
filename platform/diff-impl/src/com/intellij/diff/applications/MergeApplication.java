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

import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.merge.MergeWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public String getUsageMessage() {
    final String scriptName = ApplicationNamesInfo.getInstance().getScriptName();
    return DiffBundle.message("merge.application.usage.parameters.and.description", scriptName);
  }

  public void processCommand(@NotNull String[] args, @Nullable String currentDirectory) throws Exception {
    // TODO: try to guess 'right' project ?

    final String path1 = args[1];
    final String path2 = args[2];
    final String path3 = args[3];
    final String path4 = args.length == 5 ? args[4] : args[3];

    final VirtualFile file1 = findFile(path1, currentDirectory);
    final VirtualFile file2 = findFile(path2, currentDirectory);
    final VirtualFile file3 = findFile(path3, currentDirectory);
    final VirtualFile file4 = findFile(path4, currentDirectory);

    if (file1 == null) throw new Exception("Can't find file " + path1);
    if (file2 == null) throw new Exception("Can't find file " + path2);
    if (file3 == null) throw new Exception("Can't find file " + path3);
    if (file4 == null) throw new Exception("Can't find file " + path4);

    file1.refresh(false, true);
    file2.refresh(false, true);
    file3.refresh(false, true);
    file4.refresh(false, true);

    Project project = getProject();

    List<VirtualFile> contents = ContainerUtil.list(file1, file3, file2); // left, base, right
    MergeRequest request = DiffRequestFactory.getInstance().createMergeRequestFromFiles(project, file4, contents, null);

    new MergeWindow(project, request).show();

    Document document = FileDocumentManager.getInstance().getCachedDocument(file4);
    if (document != null) FileDocumentManager.getInstance().saveDocument(document);
  }
}
