package com.siyeh.ipp.exceptions.detail;

import java.io.IOException;

public class PolyadicParentheses {

  void box() {
      try {
          System.out.println(one() && (two()) && one());
      } catch (IOException e) {
      } catch (NoSuchFieldException e) {
      }
  }

  boolean one() throws IOException {
    return false;
  }

  boolean two() throws NoSuchFieldException {
    return false;
  }
}