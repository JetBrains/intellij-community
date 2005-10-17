package com.intellij.util.graph;

/**
 *  @author dsl
 */
class TestNode {
  private String myMark;

  public TestNode(String mark) {
    myMark = mark;
  }

  public String getMark() {
    return myMark;
  }

  public String toString() {
    return myMark;
  }
}
