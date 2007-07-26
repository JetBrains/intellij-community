package com.intellij.history.integration.revertion;

import com.intellij.history.core.ILocalVcs;
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
  protected ILocalVcs myVcs;
  protected IdeaGateway myGateway;

  protected Reverter(ILocalVcs vcs, IdeaGateway gw) {
    myVcs = vcs;
    myGateway = gw;
  }

  public String askUserForProceed() throws IOException {
    return null;
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

    myVcs.accept(selective(new ChangeVisitor() {
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
