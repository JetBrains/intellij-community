package com.intellij.localvcs;

import java.util.HashMap;
import java.util.Map;

public class TestLocalVcsStorage extends LocalVcsStorage {
  private Map<Integer, byte[]> myContents = new HashMap<Integer, byte[]>();

  public TestLocalVcsStorage() {
    super(null);
  }

  @Override
  protected void init() {
  }

  @Override
  public void save() {
  }

  @Override
  public LocalVcs.Memento load() {
    return new LocalVcs.Memento();
  }

  @Override
  public void store(LocalVcs.Memento m) {
  }

  @Override
  protected Content doCreateContent(byte[] bytes) {
    int id = myContents.size();
    myContents.put(id, bytes);
    return new Content(this, id);
  }

  @Override
  protected byte[] loadContentData(int id) {
    return myContents.get(id);
  }
}
