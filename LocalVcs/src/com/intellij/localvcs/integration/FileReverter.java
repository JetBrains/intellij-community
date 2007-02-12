package com.intellij.localvcs.integration;

import com.intellij.localvcs.Entry;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class FileReverter {
  private VirtualFile myFile;
  private Entry myOlder;

  public static void revert(VirtualFile f, Entry older) throws IOException {
    new FileReverter(f, older).revert();
  }

  private FileReverter(VirtualFile f, Entry older) {
    myFile = f;
    myOlder = older;
  }

  private void revert() throws IOException {
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
