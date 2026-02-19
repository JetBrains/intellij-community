package com.siyeh.ipp.whileloop.replace_do_while_with_while_loop;

class WithoutBraces {
  void test(boolean condition) {
      //before condition
      //after end
      if (condition) {
          System.out.println(); //before while
          while (condition) System.out.println(); //before while
      }
  }
}