package com.intellij.localvcs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class Storage {
  private File myDir;

  public Storage(File dir) {
    myDir = dir;
  }

  public ChangeList loadChangeList() {
    File f = new File(myDir, "changeList");
    if (!f.exists()) return new ChangeList();

    try {
      FileInputStream fs = new FileInputStream(f);
      try {
        Stream s = new Stream(fs);
        return s.readChangeList();
      } finally {
        fs.close();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void storeChangeList(ChangeList c) {
    File f = new File(myDir, "changeList");
    try {
      f.createNewFile();
      FileOutputStream fs = new FileOutputStream(f);
      try {
        Stream s = new Stream(fs);
        s.writeChangeList(c);
      } finally {
        fs.close();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public RootEntry loadRootEntry() {
    File f = new File(myDir, "rootEntry");
    if (!f.exists()) return new RootEntry();

    try {
      FileInputStream fs = new FileInputStream(f);
      try {
        Stream s = new Stream(fs);
        return (RootEntry)s.readEntry(); // todo cast!!!
      } finally {
        fs.close();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void storeRootEntry(RootEntry e) {
    File f = new File(myDir, "rootEntry");
    try {
      f.createNewFile();
      FileOutputStream fs = new FileOutputStream(f);
      try {
        Stream s = new Stream(fs);
        s.writeEntry(e);
      } finally {
        fs.close();
      }
    } catch (IOException e1) {
      throw new RuntimeException(e1);
    }
  }

  public Integer loadCounter() {
    File f = new File(myDir, "counter");
    if (!f.exists()) return 0;

    try {
      FileInputStream fs = new FileInputStream(f);
      try {
        Stream s = new Stream(fs);
        return s.readInteger();
      } finally {
        fs.close();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void storeCounter(Integer i) {
    File f = new File(myDir, "counter");
    try {
      f.createNewFile();
      FileOutputStream fs = new FileOutputStream(f);
      try {
        Stream s = new Stream(fs);
        s.writeInteger(i);
      } finally {
        fs.close();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
