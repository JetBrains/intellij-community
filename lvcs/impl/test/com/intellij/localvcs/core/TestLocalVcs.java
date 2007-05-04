package com.intellij.localvcs.core;

import com.intellij.localvcs.core.storage.Storage;

public class TestLocalVcs extends LocalVcs {
  private long myPurgingInterval;

  public TestLocalVcs() {
    this(new TestStorage());
  }

  public TestLocalVcs(Storage s) {
    super(s);
  }

  @Override
  public long getPurgingPeriod() {
    return myPurgingInterval;
  }

  public void setPurgingPeriod(long i) {
    myPurgingInterval = i;
  }
}
