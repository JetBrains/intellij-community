package com.siyeh.ipp.switchtoif.replace_if_with_switch;

public class Polyadic {
  void x(int i) {
      switch (i) {
          case 1:
          case 2:
          case 3:

              break;
          case 5:

              break;
          default:

              break;
      }
  }
}