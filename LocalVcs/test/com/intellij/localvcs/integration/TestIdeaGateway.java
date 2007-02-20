package com.intellij.localvcs.integration;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.concurrent.Callable;

public class TestIdeaGateway extends IdeaGateway {
  @Override
  public void runWriteAction(Callable c) {
    try {
      c.call();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
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
