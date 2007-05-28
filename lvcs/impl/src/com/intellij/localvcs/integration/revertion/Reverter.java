package com.intellij.localvcs.integration.revertion;

import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.utils.RunnableAdapter;

import java.io.IOException;
import java.util.List;

public abstract class Reverter {
  protected IdeaGateway myGateway;

  protected Reverter(IdeaGateway gw) {
    myGateway = gw;
  }

  public String askUserForProceed() throws IOException {
    return null;
  }

  public abstract List<String> checkCanRevert() throws IOException;

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
