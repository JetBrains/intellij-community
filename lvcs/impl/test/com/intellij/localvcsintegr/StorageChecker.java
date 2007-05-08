package com.intellij.localvcsintegr;

import com.intellij.localvcs.core.LocalVcs;
import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.core.storage.IContentStorage;
import com.intellij.localvcs.core.storage.Storage;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class StorageChecker {
  public static void main(String[] args) {
    Storage s = new Storage(new File("C:/temp/1")) {
      @Override
      protected IContentStorage createContentStorage() {
        return new NullContentStorage();
      }
    };

    LocalVcs vcs = new LocalVcs(s);
    List<Revision> rr = vcs.getRevisionsFor("C:/ClearCase/vob_rmt_compl/FlashUtil");

    System.out.println("");
  }

  private static class NullContentStorage implements IContentStorage {
    public void close() {
    }

    public void save() {
    }

    public int store(byte[] content) throws IOException {
      return 0;
    }

    public byte[] load(int id) throws IOException {
      return new byte[0];
    }

    public void remove(int id) {
    }

    public boolean isRemoved(int id) {
      return false;
    }
  }
}
