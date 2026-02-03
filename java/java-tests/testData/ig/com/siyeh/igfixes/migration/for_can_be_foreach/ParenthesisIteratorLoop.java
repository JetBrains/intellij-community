import java.util.*;

class X {

  void x(List<String> list) {
    for<caret> (Iterator<String> /*1*/iterator = ((list).iterator()); ((iterator)./*2*/hasNext()); /*3*/) {
      System.out.println(((iterator/*4*/).next()));
    }
  }
}