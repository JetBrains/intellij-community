package com.intellij.localvcs.integration;

import com.intellij.localvcs.Entry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class Reverter {
  public static void revert(LocalFileSystem fs, Entry newer, Entry older) throws IOException {
    VirtualFile f = fs.findFileByPath(newer.getPath());

    if (older == null) {
      f.delete(null);
      return;
    }

    if (!older.getName().equals(f.getName())) {
      f.rename(null, older.getName());
    }

    if (f.getTimeStamp() != older.getTimestamp()) {
      f.setBinaryContent(older.getContent().getBytes(), -1, older.getTimestamp());
    }
  }
}
