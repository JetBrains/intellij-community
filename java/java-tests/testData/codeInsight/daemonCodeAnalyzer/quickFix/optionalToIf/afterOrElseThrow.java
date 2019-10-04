// "Fix all 'Optional can be replaced with sequence of if statements' problems in file" "true"

import java.util.*;

class Test {

  String orElseThrowDefault(String in) {
      if (in == null || in.length() <= 12) throw new NoSuchElementException("No value present");
      return in;
  }

  String orElseLambda(String in) {
      if (in == null || in.length() <= 12) throw new IllegalArgumentException("value is null");
      return in;
  }

  String orElseThrowWithSideEffect(String in) {
      if (in == null) throw sideEffect();
      String s = in.substring(3);
      if (s.length() <= 12) throw sideEffect();
      return s;
  }

  private RuntimeException sideEffect() {
    System.out.println("side effect!");
    return new IllegalArgumentException("value is null")
  }

}