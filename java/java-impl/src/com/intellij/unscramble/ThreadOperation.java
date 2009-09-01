package com.intellij.unscramble;

/**
 * @author yole
 */
public enum ThreadOperation {
  Socket("socket operation"), IO("I/O");

  private final String myName;

  ThreadOperation(final String name) {
    myName = name;
  }

  public String toString() {
    return myName;
  }
}
