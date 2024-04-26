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

package com.intellij.history.integration.revertion;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public abstract class Reverter {
  private final Project myProject;
  protected LocalHistoryFacade myVcs;
  protected final IdeaGateway myGateway;
  private final Supplier<@NlsContexts.Command String> myCommandName;

  protected Reverter(Project p, LocalHistoryFacade vcs, IdeaGateway gw, @NotNull Supplier<@NlsContexts.Command String> commandName) {
    myProject = p;
    myVcs = vcs;
    myGateway = gw;
    myCommandName = commandName;
  }

  /**
   * @deprecated always returns empty list
   */
  @Deprecated
  public List<String> askUserForProceeding() throws IOException {
    return Collections.emptyList();
  }

  public List<String> checkCanRevert() throws IOException {
    if (!askForReadOnlyStatusClearing()) {
      return Collections.singletonList(LocalHistoryBundle.message("revert.error.files.are.read.only"));
    }
    return Collections.emptyList();
  }

  protected boolean askForReadOnlyStatusClearing() {
    return myGateway.ensureFilesAreWritable(myProject, getFilesToClearROStatus());
  }

  public void revert() throws Exception {
    try {
      WriteCommandAction.writeCommandAction(myProject).withName(getCommandName()).run(() -> {
        myGateway.saveAllUnsavedDocuments();
        doRevert();
        myGateway.saveAllUnsavedDocuments();
      });
    }
    catch (RuntimeException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw (IOException)cause;
      }
      throw e;
    }
  }

  public @NlsContexts.Command String getCommandName() {
    return myCommandName.get();
  }

  protected abstract @NotNull List<VirtualFile> getFilesToClearROStatus();

  protected abstract void doRevert() throws IOException;

  @NotNull
  public static @Nls String getRevertCommandName(@NotNull Revision to) {
    String name = to.getChangeSetName();
    String date = DateFormatUtil.formatDateTime(to.getTimestamp());
    if (name != null) {
      return LocalHistoryBundle.message("system.label.revert.to.change.date", name, date);
    }
    return LocalHistoryBundle.message("system.label.revert.to.date", date);
  }
}
