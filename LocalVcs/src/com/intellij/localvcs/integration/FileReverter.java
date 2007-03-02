package com.intellij.localvcs.integration;

import com.intellij.localvcs.Entry;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.concurrent.Callable;

public class FileReverter {
  private IdeaGateway myIdeaGateway;
  private VirtualFile myFile;
  private Entry myOlder;

  public static boolean revert(IdeaGateway gw, VirtualFile f, Entry older) {
    return new FileReverter(gw, f, older).revert();
  }

  private FileReverter(IdeaGateway gw, VirtualFile f, Entry older) {
    myIdeaGateway = gw;
    myFile = f;
    myOlder = older;
  }

  private boolean revert() {
    return myIdeaGateway.runWriteAction(new Callable<Boolean>() {
      public Boolean call() throws Exception {
        if (!myIdeaGateway.ensureFilesAreWritable(myFile)) return false;
        doRevert();
        return true;
      }
    });
  }

  private void doRevert() throws IOException {
    if (myOlder == null) {
      myFile.delete(null);
    }
    else {
      revertChanges();
    }
  }

  private void revertChanges() throws IOException {
    if (!myFile.getName().equals(myOlder.getName())) {
      myFile.rename(null, myOlder.getName());
    }
    if (myFile.getTimeStamp() != myOlder.getTimestamp()) {
      myFile.setBinaryContent(myOlder.getContent().getBytes(), -1, myOlder.getTimestamp());
    }
  }
}
