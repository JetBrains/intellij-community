package com.intellij.localvcs;

public class TestLocalVcs extends LocalVcs {
  private long myTimestamp;
  private long myPurgingInterval;

  public TestLocalVcs() {
    this(new TestStorage());
  }

  public TestLocalVcs(Storage s) {
    super(s);
  }

  @Override
  public long getCurrentTimestamp() {
    return myTimestamp;
  }

  public void setCurrentTimestamp(long t) {
    myTimestamp = t;
  }

  @Override
  public long getPurgingInterval() {
    return myPurgingInterval;
  }

  public void setPurgingInterval(long i) {
    myPurgingInterval = i;
  }
}
