package com.siyeh.ipp.whileloop.replace_while_with_do_while_loop;

class Regular {

  void m() {
    <caret>while(b()) {
      System.out.println(1);
    }
  }

  boolean b() {
    return true;
  }
}