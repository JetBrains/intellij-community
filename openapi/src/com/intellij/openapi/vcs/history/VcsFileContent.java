package com.intellij.openapi.vcs.history;

import com.intellij.openapi.vcs.VcsException;

import java.io.IOException;

public interface VcsFileContent {
  void loadContent() throws VcsException;

  byte[] getContent() throws IOException;
}
