// "Replace condition with Objects.requireNonNullElse" "true"

import java.util.*;

class Test {
  void work(Object o) {

  }

  public void test(Object o) {
    work(/*1*/o/*2*/ <caret>== /*3*/null/*4*/?/*5*/ ""/*6*/ :/*7*/ o/*8*/);
  }
}