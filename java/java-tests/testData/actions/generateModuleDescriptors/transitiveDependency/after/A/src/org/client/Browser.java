package org.client;

import org.driver.factory.ApiFactory;

public class Browser {
  public static void main(String[] args) {
    ApiFactory.create().exec();
  }
}
