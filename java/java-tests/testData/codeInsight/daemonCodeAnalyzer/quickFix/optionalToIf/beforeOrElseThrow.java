// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.*;

class Test {

  String orElseThrowDefault(String in) {
    return Optional.ofNullable<caret>(in).filter(s -> s.length() > 12).orElseThrow();
  }

  String orElseLambda(String in) {
    return Optional.ofNullable(in).filter(s -> s.length() > 12).orElseThrow(() -> new IllegalArgumentException("value is null"));
  }

  String orElseThrowWithSideEffect(String in) {
    return Optional.ofNullable(in).map(s -> s.substring(3)).filter(s -> s.length() > 12).orElseThrow(() -> sideEffect());
  }

  private RuntimeException sideEffect() {
    System.out.println("side effect!");
    return new IllegalArgumentException("value is null")
  }

}