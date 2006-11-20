package com.intellij.localvcs;

class TestStorage extends Storage {
  public TestStorage() { super(null); }

  @Override
  public ChangeList loadChangeList() { return new ChangeList(); }

  @Override
  public RootEntry loadRootEntry() { return new RootEntry(""); }

  @Override
  public Integer loadCounter() { return 0; }
}
