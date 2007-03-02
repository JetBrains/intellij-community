package com.intellij.localvcs.integration;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.concurrent.Callable;

public class TestIdeaGateway extends IdeaGateway {
  public TestIdeaGateway() {
    super(null);
  }

  @Override
  public <T> T runWriteAction(Callable<T> c) {
    try {
      return c.call();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean ensureFilesAreWritable(VirtualFile... ff) {
    return true;
  }

  @Override
  public byte[] getDocumentByteContent(VirtualFile f) {
    try {
      return f.contentsToByteArray();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
