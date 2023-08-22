package com.siyeh.ipp.whileloop.replace_while_with_do_while_loop;

class InfiniteLoop {

  void m() {
      do {
          System.out.println(1);
      } while ((true));
  }
}