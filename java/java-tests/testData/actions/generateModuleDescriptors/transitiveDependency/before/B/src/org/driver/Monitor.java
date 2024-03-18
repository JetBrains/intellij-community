package org.driver;

import org.declaration.API;

public class Monitor implements API {
  @Override
  public void exec() {
    System.out.println("monitor");
  }

  public static String test() {
    return "test";
  }
}
