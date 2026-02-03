package org.driver.factory;

import org.declaration.API;
import org.driver.Monitor;

public class ApiFactory {
  public static API create() {
    return new Monitor();
  }
}
