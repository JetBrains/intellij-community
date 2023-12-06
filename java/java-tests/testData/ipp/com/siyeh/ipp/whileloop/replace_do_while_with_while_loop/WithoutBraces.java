package com.siyeh.ipp.whileloop.replace_do_while_with_while_loop;

class WithoutBraces {
  void test(boolean condition) {
    if (condition)
    <caret>do System.out.println(); //before while
    while //before condition
      (condition); //after end
  }
}