package com.siyeh.ipp.exceptions.detail;

class Disjunction {

  void foo() {
    t<caret>ry{
      if (true) {
        throw new IllegalArgumentException();
      } else {
        throw new NullPointerException();
      }
    } catch (IllegalArgumentException | NullPointerException e) {

    }
  }
}