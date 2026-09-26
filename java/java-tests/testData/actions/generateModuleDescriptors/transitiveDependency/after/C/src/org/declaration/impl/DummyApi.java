package org.declaration.impl;

import org.declaration.API;

public class DummyApi implements API {
  @Override
  public void exec() {
  }

  public static String test() {
    return "test";
  }
}
