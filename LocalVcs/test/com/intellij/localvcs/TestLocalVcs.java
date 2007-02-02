package com.intellij.localvcs;

public class TestLocalVcs extends LocalVcs {
  public TestLocalVcs() {
    super(new TestStorage());
  }
}
