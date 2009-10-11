/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.IdPath;
import com.intellij.history.core.changes.ChangeVisitor;
import com.intellij.history.core.changes.StructuralChange;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.*;

public abstract class Reverter {
  protected LocalVcs myVcs;
  protected IdeaGateway myGateway;

  protected Reverter(LocalVcs vcs, IdeaGateway gw) {
    myVcs = vcs;
    myGateway = gw;
  }

  public List<String> askUserForProceeding() throws IOException {
    return Collections.emptyList();
  }

  public List<String> checkCanRevert() throws IOException {
    List<String> errors = new ArrayList<String>();
    doCheckCanRevert(errors);
    return removeDuplicatesAndSort(errors);
  }

  private List<String> removeDuplicatesAndSort(List<String> list) {
    List<String> result = new ArrayList<String>(new HashSet<String>(list));
    Collections.sort(result);
    return result;
  }

  protected void doCheckCanRevert(List<String> errors) throws IOException {
    if (!askForReadOnlyStatusClearing()) {
      errors.add(LocalHistoryBundle.message("revert.error.files.are.read.only"));
    }
  }

  protected boolean askForReadOnlyStatusClearing() throws IOException {
    return myGateway.ensureFilesAreWritable(getFilesToClearROStatus());
  }

  protected List<VirtualFile> getFilesToClearROStatus() throws IOException {
    final Set<VirtualFile> files = new HashSet<VirtualFile>();

    myVcs.acceptRead(selective(new ChangeVisitor() {
      @Override
      public void visit(StructuralChange c) {
        for (IdPath p : c.getAffectedIdPaths()) {
          Entry e = myRoot.findEntry(p);
          if (e == null) continue;
          files.addAll(myGateway.getAllFilesFrom(e.getPath()));
        }
      }
    }));

    return new ArrayList<VirtualFile>(files);
  }

  protected ChangeVisitor selective(ChangeVisitor v) {
    return v;
  }

  public void revert() throws IOException {
    try {
      myGateway.performCommandInsideWriteAction(formatCommandName(), new RunnableAdapter() {
        @Override
        public void doRun() throws Exception {
          myGateway.saveAllUnsavedDocuments();
          doRevert();
          myGateway.saveAllUnsavedDocuments();
        }
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

  protected abstract String formatCommandName();

  protected abstract void doRevert() throws IOException;
}
