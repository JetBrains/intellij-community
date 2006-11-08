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

  public ChangeList loadChangeList() throws IOException {
    File f = new File(myDir, "changeList");
    FileInputStream fs = new FileInputStream(f);
    try {
      Stream s = new Stream(fs);
      return s.readChangeList();
    } finally {
      fs.close();
    }
  }

  public void storeChangeList(ChangeList c) throws IOException {
    File f = new File(myDir, "changeList");
    f.createNewFile();
    FileOutputStream fs = new FileOutputStream(f);
    try {
      Stream s = new Stream(fs);
      s.writeChangeList(c);
    } finally {
      fs.close();
    }
  }

  public RootEntry loadRootEntry() throws IOException {
    File f = new File(myDir, "rootEntry");
    FileInputStream fs = new FileInputStream(f);
    try {
      Stream s = new Stream(fs);
      return (RootEntry)s.readEntry(); // todo cast!!!
    } finally {
      fs.close();
    }
  }

  public void storeRootEntry(RootEntry e) throws IOException {
    File f = new File(myDir, "rootEntry");
    f.createNewFile();
    FileOutputStream fs = new FileOutputStream(f);
    try {
      Stream s = new Stream(fs);
      s.writeEntry(e);
    } finally {
      fs.close();
    }
  }

  public Integer loadCounter() throws IOException {
    File f = new File(myDir, "counter");
    FileInputStream fs = new FileInputStream(f);
    try {
      Stream s = new Stream(fs);
      return s.readInteger();
    } finally {
      fs.close();
    }
  }

  public void storeCounter(Integer i) throws IOException {
    File f = new File(myDir, "counter");
    f.createNewFile();
    FileOutputStream fs = new FileOutputStream(f);
    try {
      Stream s = new Stream(fs);
      s.writeInteger(i);
    } finally {
      fs.close();
    }
  }
}
