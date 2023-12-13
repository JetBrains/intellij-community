package org.declaration.impl;

import org.declaration.API;

public class DummyApi implements API {
  @Override
  public String name() {
    return "dummy";
  }
}
