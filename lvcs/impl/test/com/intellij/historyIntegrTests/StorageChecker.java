package com.intellij.historyIntegrTests;

import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.TestLocalVcs;
import com.intellij.history.core.changes.Change;
import com.intellij.history.core.storage.IContentStorage;
import com.intellij.history.core.storage.Storage;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.FormatUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

public class StorageChecker {
  public static void main(String[] args) {
    Storage s = new Storage(new File("C:/temp/1")) {
      @Override
      protected IContentStorage createContentStorage() {
        return new NullContentStorage();
      }
    };

    LocalVcs vcs = new TestLocalVcs(s);
    LocalVcs.Memento m = s.load();
    Entry e = m.myRoot.getEntry(43077);
    //List<Change> cc = m.myChangeList.getChangesFor(m.myRoot.copy(), e.getPath());
    List<Change> cc = m.myChangeList.getChanges();
    List<DateAndChange> result = new ArrayList<DateAndChange>();
    for (Change c : cc) {
      Calendar d = new GregorianCalendar();
      d.setTimeInMillis(c.getTimestamp());

      int h = d.get(Calendar.HOUR_OF_DAY);
      if (d.get(Calendar.DAY_OF_MONTH) == 22 && h > 13 & h < 20) {
        result.add(new DateAndChange(c));
      }
    }


    System.out.println("");
  }

  static class DateAndChange {
    Change c;

    public DateAndChange(final Change c) {
      this.c = c;
    }

    @Override
    public String toString() {
      return FormatUtil.formatTimestamp(c.getTimestamp()) + c.getName() + "(" + c.getClass().getSimpleName() + ")";
    }
  }

  private static class NullContentStorage implements IContentStorage {
    public void close() {
    }

    public int store(byte[] content) throws IOException {
      return 0;
    }

    public byte[] load(int id) throws IOException {
      return new byte[0];
    }

    public void remove(int id) {
    }

    public int getVersion() {
      return 1;
    }

    public void setVersion(final int version) {
    }
  }
}
