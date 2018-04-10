// "Split into filter chain" "true"

import java.util.List;

class Test {
  void f(List<String> l) {
    l.stream().filter(s -> s/*4*/ !=/*5*/ null/*6*/).anyMatch(/*1*/s/*2*/ ->/*3*//*7*/!s./*8*/isEmpty()/*9*//*10*/);
  }
}