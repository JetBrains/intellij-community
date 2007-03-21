package com.intellij.localvcs;

public class TestLocalVcs extends LocalVcs {
  private long myPurgingInterval;

  public TestLocalVcs() {
    this(new TestLocalVcsStorage());
  }

  public TestLocalVcs(LocalVcsStorage s) {
    super(s);
  }

  @Override
  public long getPurgingInterval() {
    return myPurgingInterval;
  }

  public void setPurgingInterval(long i) {
    myPurgingInterval = i;
  }
}
