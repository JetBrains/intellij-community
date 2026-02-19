package com.siyeh.ipp.switchtoif.replace_if_with_switch;

public class Polyadic {
  void x(int i) {
    <caret>if (i == 1 || i == 2 || i == 3) {

    } else if (i == 5) {

    } else {

    }
  }
}