import java.util.*;

class Parentheses {

  void x(List<String> list) {
    final var iterator = /*1*/((list/*1b*/)./*2*/iterator());
    while<caret>/*3*/ (((iterator)/*4*/.hasNext()))/*5*/ {
      out.println(((iterator)./*6*/next()));/*7*/
    }
  }
}