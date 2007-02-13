package com.intellij.localvcs;

public class TestLocalVcs extends LocalVcs {
  private long myTimestamp;

  public TestLocalVcs() {
    this(new TestStorage());
  }

  public TestLocalVcs(Storage s) {
    super(s);
  }

  @Override
  public long getTimestamp() {
    return myTimestamp;
  }

  public void setTimestamp(long t) {
    myTimestamp = t;
  }
}
