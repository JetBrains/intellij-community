package com.intellij.localvcs;

import com.intellij.openapi.util.io.FileUtil;

import java.io.*;
import java.util.List;

public class LocalVcsStorage {
  private static final int VERSION = 7;

  private File myDir;
  private IContentStorage myContentStorage;

  public LocalVcsStorage(File dir) {
    myDir = dir;
    init();
  }

  protected void init() {
    checkVersionAndCreateDir();
    initContentStorage();
  }

  private void checkVersionAndCreateDir() {
    int version = load("version", -1, new Loader<Integer>() {
      public Integer load(Stream s) throws IOException {
        return s.readInteger();
      }
    });

    if (version != getVersion()) recreateStorage();

    store("version", new Storer() {
      public void store(Stream s) throws IOException {
        s.writeInteger(getVersion());
      }
    });
  }

  private void recreateStorage() {
    FileUtil.delete(myDir);
    myDir.mkdirs();
  }

  private void initContentStorage() {
    try {
      myContentStorage = ContentStorage.createContentStorage(new File(myDir, "contents"));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public LocalVcs.Memento load() {
    return load("storage", new LocalVcs.Memento(), new Loader<LocalVcs.Memento>() {
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
    store("storage", new Storer() {
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
      throw new RuntimeException(e);
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

  public Content createContent(byte[] bytes) {
    if (bytes.length > LongContent.MAX_LENGTH) return new LongContent();
    return doCreateContent(bytes);
  }

  protected Content doCreateContent(byte[] bytes) {
    try {
      int id = myContentStorage.store(bytes);
      return new Content(this, id);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected byte[] loadContentData(int id) {
    try {
      return myContentStorage.load(id);
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
