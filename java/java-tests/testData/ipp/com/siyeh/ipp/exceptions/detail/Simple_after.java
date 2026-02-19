package com.siyeh.ipp.exceptions.detail;

class Simple {

  void foo() {
      try {
          if (true) {
              throw new IllegalArgumentException();
          } else {
              throw new NullPointerException();
          }
      } catch (IllegalArgumentException e) {

      } catch (NullPointerException e) {

      }
  }
}