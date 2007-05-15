package com.intellij.localvcsintegr;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.LocalVcs;
import com.intellij.localvcs.core.changes.Change;
import com.intellij.localvcs.core.changes.StructuralChange;
import com.intellij.localvcs.core.storage.IContentStorage;
import com.intellij.localvcs.core.storage.Storage;
import com.intellij.localvcs.core.tree.Entry;

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
    LocalVcs.Memento m = s.load();
    Entry e = m.myRoot.getEntry(43077);
    //List<Change> cc = m.myChangeList.getChangesFor(m.myRoot.copy(), e.getPath());
    List<Change> cc = m.myChangeList.getChanges();
    for (Change change : cc) {
      for (Change c : change.getChanges()) {
        if (c instanceof StructuralChange) {
          StructuralChange sc = (StructuralChange)c;
          IdPath path = new IdPath(38781, 42721, 42722, 42726, 43058, 43070, 43076, 43077, 103769);
          if (sc.getAffectedIdPaths()[0].startsWith(path)) {
            System.out.println("");
          }
        }
      }
    }


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
