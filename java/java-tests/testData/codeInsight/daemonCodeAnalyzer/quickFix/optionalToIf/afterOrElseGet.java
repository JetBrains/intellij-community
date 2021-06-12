// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.*;

class Test {

  void assignment(String in) {
      String out = "foo";
      if (in != null && in.length() > 2) out = in;
  }


  void assignmentWithSideEffect(String in) {
      String result = null;
      if (in != null && in.length() > 2) result = in;
      if (result == null) result = sideEffect();
      String out = result;
  }

  private String sideEffect() {
    System.out.println("side effect");
    return "foo";
  }


}