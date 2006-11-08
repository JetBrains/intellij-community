package com.intellij.localvcs;

class TestStorage extends Storage {
  public TestStorage() { super(null); }

  @Override
  public ChangeList loadChangeList() { return new ChangeList(); }

  @Override
  public RootEntry loadRootEntry() { return new RootEntry(); }

  @Override
  public Integer loadCounter() { return 0; }

  @Override
  public void storeChangeList(ChangeList c) {}

  @Override
  public void storeRootEntry(RootEntry e) {}

  @Override
  public void storeCounter(Integer i) {}
}
