package org.driver;

import org.driver.factory.ApiFactory;

public class Monitor {
  public void exec() {
    System.out.println(ApiFactory.create().name());
  }
}
