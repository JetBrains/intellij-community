package com.intellij.localvcs.integration;

import com.intellij.localvcs.Entry;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.concurrent.Callable;

public class FileReverter {
  private IdeaGateway myIdeaGateway;
  private VirtualFile myFile;
  private Entry myOlder;

  public static void revert(IdeaGateway gw, VirtualFile f, Entry older) {
    new FileReverter(gw, f, older).revert();
  }

  private FileReverter(IdeaGateway gw, VirtualFile f, Entry older) {
    myIdeaGateway = gw;
    myFile = f;
    myOlder = older;
  }

  private void revert() {
    myIdeaGateway.runWriteAction(new Callable() {
      public Object call() throws Exception {
        if (!myIdeaGateway.ensureFilesAreWritable(myFile)) return null;
        doRevert();
        return null;
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
