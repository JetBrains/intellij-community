// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.*;

class Test {

  void assignment(String in) {
    String out = Optional.ofNullable<caret>(in).filter(s -> s.length() > 2).orElseGet(() -> "foo");
  }


  void assignmentWithSideEffect(String in) {
    String out = Optional.ofNullable(in).filter(s -> s.length() > 2).orElseGet(() -> sideEffect());
  }

  private String sideEffect() {
    System.out.println("side effect");
    return "foo";
  }


}