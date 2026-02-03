package com.siyeh.ipp.conditional.withIf;

class Comment {
  public String get() {
      /*before then*/
      /*after then*/
      if (239 > 42) return "239";
          //comment
      return "42";
  }
}