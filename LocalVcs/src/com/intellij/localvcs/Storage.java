package com.intellij.localvcs;

import com.intellij.openapi.util.io.FileUtil;

import java.io.*;

public class Storage {
  private static final int VERSION = 4;

  private File myDir;
  private IContentStorage myContentStorage;

  public Storage(File dir) {
    myDir = dir;
    init();
  }

  protected void init() {
    checkVersionAndCreateDir();

    try {
      myContentStorage = new ContentStorage(new File(myDir, "contents"));
      myContentStorage = new CachingContentStorage(myContentStorage);
      myContentStorage = new ThreadSafeContentStorage(myContentStorage);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void checkVersionAndCreateDir() {
    int version = load("version", getVersion() - 1, new Loader<Integer>() {
      public Integer load(Stream s) throws IOException {
        return s.readInteger();
      }
    });

    if (version != getVersion()) FileUtil.delete(myDir);
    myDir.mkdirs();

    store("version", new Storer() {
      public void store(Stream s) throws IOException {
        s.writeInteger(getVersion());
      }
    });
  }

  protected int getVersion() {
    return VERSION;
  }

  public void close() {
    try {
      myContentStorage.close();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void save() {
    try {
      myContentStorage.save();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public ChangeList loadChangeList() {
    return load("changes", new ChangeList(), new Loader<ChangeList>() {
      public ChangeList load(Stream s) throws IOException {
        return s.readChangeList();
      }
    });
  }

  public void storeChangeList(final ChangeList c) {
    store("changes", new Storer() {
      public void store(Stream s) throws IOException {
        s.writeChangeList(c);
      }
    });
  }

  public RootEntry loadRootEntry() {
    return load("entries", new RootEntry(), new Loader<RootEntry>() {
      public RootEntry load(Stream s) throws IOException {
        return (RootEntry)s.readEntry(); // todo cast!!!
      }
    });
  }

  public void storeRootEntry(final RootEntry e) {
    store("entries", new Storer() {
      public void store(Stream s) throws IOException {
        s.writeEntry(e);
      }
    });
  }

  public Integer loadCounter() {
    return load("counter", 0, new Loader<Integer>() {
      public Integer load(Stream s) throws IOException {
        return s.readInteger();
      }
    });
  }

  public void storeCounter(final Integer i) {
    store("counter", new Storer() {
      public void store(Stream s) throws IOException {
        s.writeInteger(i);
      }
    });
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

  protected byte[] loadContent(int id) {
    try {
      return myContentStorage.load(id);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static interface Loader<T> {
    T load(Stream s) throws IOException;
  }

  private static interface Storer {
    void store(Stream s) throws IOException;
  }
}
