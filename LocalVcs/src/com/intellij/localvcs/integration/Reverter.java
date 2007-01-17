package com.intellij.localvcs.integration;

import com.intellij.localvcs.Entry;
import com.intellij.localvcs.Label;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class Reverter {
  public static void revert(LocalFileSystem fs, String path, Entry old) throws IOException {
    VirtualFile cur = fs.findFileByPath(path);

    if (!old.getName().equals(cur.getName())) {
      cur.rename(null, old.getName());
    }

    if (cur.getTimeStamp() != old.getTimestamp()) {
      cur.setBinaryContent(old.getContent().getBytes(), -1, old.getTimestamp());
    }
  }
}
