package com.intellij.localvcs;

import java.util.HashMap;
import java.util.Map;

public class TestStorage extends Storage {
  private Map<Integer, byte[]> myContents = new HashMap<Integer, byte[]>();

  public TestStorage() {
    super(null);
  }

  @Override
  protected void init() {
  }

  @Override
  public ChangeList loadChangeList() {
    return new ChangeList();
  }

  @Override
  public RootEntry loadRootEntry() {
    return new RootEntry();
  }

  @Override
  public Integer loadCounter() {
    return 0;
  }

  @Override
  protected Content doCreateContent(byte[] bytes) {
    int id = myContents.size();
    myContents.put(id, bytes);
    return new Content(this, id);
  }

  @Override
  protected byte[] loadContent(int id) {
    return myContents.get(id);
  }
}
