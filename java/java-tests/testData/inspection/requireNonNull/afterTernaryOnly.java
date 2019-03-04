// "Replace condition with Objects.requireNonNullElse" "true"

import java.util.*;

class Test {
  void work(Object o) {

  }

  public void test(Object o) {
      /*2*/
      /*3*/
      /*4*/
      /*5*/
      /*6*/
      /*7*/
      work(/*1*/Objects.requireNonNullElse(o, "")/*8*/);
  }
}