package com.intellij.localvcs.integration;

import com.intellij.localvcs.Entry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class Reverter {
  public static void revert(LocalFileSystem fs, Entry newer, Entry older) throws IOException {

    if (newer == null) {
      // todo what if there is no parent??? is it possible?
      VirtualFile dir = fs.findFileByPath(older.getParent().getPath());
      VirtualFile f = dir.createChildData(null, older.getName());
      f.setBinaryContent(older.getContent().getBytes(), -1, older.getTimestamp());
      return;
    }

    VirtualFile f = fs.findFileByPath(newer.getPath());

    if (older == null) {
      f.delete(null);
      return;
    }

    // todo test case-insensitivity
    if (!older.getName().equals(f.getName())) {
      f.rename(null, older.getName());
    }

    if (f.getTimeStamp() != older.getTimestamp()) {
      f.setBinaryContent(older.getContent().getBytes(), -1, older.getTimestamp());
    }
  }
}
