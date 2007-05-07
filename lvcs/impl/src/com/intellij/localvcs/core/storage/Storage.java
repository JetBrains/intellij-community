package com.intellij.localvcs.core.storage;

import com.intellij.localvcs.core.LocalVcs;
import com.intellij.localvcs.core.tree.RootEntry;
import com.intellij.openapi.util.io.FileUtil;

import java.io.*;
import java.util.List;

public class Storage {
  private static final int VERSION = 14;
  private static final String BROKEN_MARK_FILE = ".broken";
  private static final String VERSION_FILE = "version";
  private static final String STORAGE_FILE = "storage";
  private static final String CONTENTS_FILE = "contents";

  private File myDir;
  private IContentStorage myContentStorage;

  private boolean isBroken = false;

  public Storage(File dir) {
    myDir = dir;
    initStorage();
  }

  protected void initStorage() {
    validate();
    initContentStorage();
  }

  private void validate() {
    if (wasMarkedAsBroken() || isValidVersion()) {
      deleteStorage();
      myDir.mkdirs();
      storeVersion();
    }
  }

  private void deleteStorage() {
    FileUtil.delete(myDir);
  }

  private boolean wasMarkedAsBroken() {
    return new File(myDir, BROKEN_MARK_FILE).exists();
  }

  private boolean isValidVersion() {
    int version = load(VERSION_FILE, -1, new Loader<Integer>() {
      public Integer load(Stream s) throws IOException {
        return s.readInteger();
      }
    });

    return version != getVersion();
  }

  private void storeVersion() {
    store(VERSION_FILE, new Storer() {
      public void store(Stream s) throws IOException {
        s.writeInteger(getVersion());
      }
    });
  }

  private void initContentStorage() {
    try {
      myContentStorage = ContentStorage.createContentStorage(new File(myDir, CONTENTS_FILE));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public LocalVcs.Memento load() {
    return load(STORAGE_FILE, new LocalVcs.Memento(), new Loader<LocalVcs.Memento>() {
      public LocalVcs.Memento load(Stream s) throws IOException {
        LocalVcs.Memento m = new LocalVcs.Memento();
        m.myRoot = (RootEntry)s.readEntry();
        m.myEntryCounter = s.readInteger();
        m.myChangeList = s.readChangeList();
        return m;
      }
    });
  }

  public void store(final LocalVcs.Memento m) {
    store(STORAGE_FILE, new Storer() {
      public void store(Stream s) throws IOException {
        s.writeEntry(m.myRoot);
        s.writeInteger(m.myEntryCounter);
        s.writeChangeList(m.myChangeList);
      }
    });
  }

  protected int getVersion() {
    return VERSION;
  }

  public void close() {
    myContentStorage.close();
  }

  public void save() {
    myContentStorage.save();
  }

  private <T> T load(String fileName, T def, Loader<T> loader) {
    File f = new File(myDir, fileName);
    if (!f.exists()) return def;

    try {
      InputStream fs = new BufferedInputStream(new FileInputStream(f));
      try {
        return loader.load(new Stream(fs, this));
      }
      finally {
        fs.close();
      }
    }
    catch (IOException e) {
      deleteStorage();
      initStorage();
      return def;
    }
  }

  private void store(String fileName, Storer storer) {
    File f = new File(myDir, fileName);
    try {
      f.createNewFile();
      OutputStream fs = new BufferedOutputStream(new FileOutputStream(f));
      try {
        storer.store(new Stream(fs));
      }
      finally {
        fs.close();
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Content storeContent(byte[] bytes) {
    if (isBroken) return new UnavailableContent();

    try {
      int id = myContentStorage.store(bytes);
      return new Content(this, id);
    }
    catch (IOException e) {
      markAsBroken();
      return new UnavailableContent();
    }
  }

  protected byte[] loadContentData(int id) throws IOException {
    if (isBroken) throw new IOException();
    try {
      return myContentStorage.load(id);
    }
    catch (IOException e) {
      markAsBroken();
      throw e;
    }
  }

  private void markAsBroken() {
    isBroken = true;
    try {
      new File(myDir, BROKEN_MARK_FILE).createNewFile();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void purgeContents(List<Content> contents) {
    for (Content c : contents) c.purge();
  }

  protected void purgeContent(Content c) {
    myContentStorage.remove(c.getId());
  }

  public boolean isContentPurged(Content c) {
    return myContentStorage.isRemoved(c.getId());
  }

  private static interface Loader<T> {
    T load(Stream s) throws IOException;
  }

  private static interface Storer {
    void store(Stream s) throws IOException;
  }
}
