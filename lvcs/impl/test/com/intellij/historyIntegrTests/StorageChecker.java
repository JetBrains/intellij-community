package com.intellij.historyIntegrTests;

import com.intellij.history.core.changes.Change;
import com.intellij.history.core.storage.BrokenStorageException;
import com.intellij.history.core.storage.IContentStorage;
import com.intellij.history.core.storage.Storage;
import com.intellij.history.integration.FormatUtil;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;

public class StorageChecker {
  public static void main(String[] args) {
    final File dir = new File("C:/temp/local_history_mine");
    FileUtil.delete(new File(dir, ".broken"));

    Storage s = new Storage(dir);

    s.checkIntegrity();

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
    public void save() {
    }

    public void close() {
    }

    public int store(byte[] content) throws BrokenStorageException {
      return 0;
    }

    public byte[] load(int id) throws BrokenStorageException {
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
