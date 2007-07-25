package com.intellij.history.core;

import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.Storage;
import com.intellij.history.core.storage.StoredContent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class InMemoryStorage extends Storage {
  private Map<Integer, byte[]> myContents = new HashMap<Integer, byte[]>();

  public InMemoryStorage() {
    super(null);
  }

  @Override
  protected void initStorage() {
  }

  @Override
  public LocalVcs.Memento load() {
    return new LocalVcs.Memento();
  }

  @Override
  public void store(LocalVcs.Memento m) {
  }

  @Override
  public Content storeContent(byte[] bytes) {
    int id = myContents.size();
    myContents.put(id, bytes);
    return new StoredContent(this, id);
  }

  @Override
  protected byte[] loadContentData(int id) throws IOException {
    return myContents.get(id);
  }

  @Override
  protected void purgeContent(StoredContent c) {
  }
}
