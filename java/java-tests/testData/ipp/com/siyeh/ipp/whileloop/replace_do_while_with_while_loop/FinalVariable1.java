package com.siyeh.ipp.whileloop.replace_do_while_with_while_loop;

class FinalVariable1 {
  void m() {
    <caret>do {
      final int j = 10;
      System.out.println(j);
    } while (c());
  }

  boolean c() {
    return true;
  }
}