package com.intellij.ui.growl;

import com.sun.jna.NativeLong;

/**
 * @author spleaner
 */
public class Selector extends NativeLong {

  private String myName;

  public Selector() {
    this("undefined selector", 0);
  }

  public Selector(String name, long value) {
    super(value);
    myName = name;
  }

  public String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return String.format("[Selector %s]", myName);
  }

  public Selector initName(final String name) {
    myName = name;
    return this;
  }
}
