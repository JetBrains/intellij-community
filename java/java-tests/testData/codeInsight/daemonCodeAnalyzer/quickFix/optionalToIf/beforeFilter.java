// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.*;

class Test {

  String checkNonConstantCondition(String in) {
    return Optional.ofNullable<caret>(in).filter(s -> s.length() > 42).orElse("foo");
  }

  String removeCheckForConstantCondition(String in) {
    return Optional.ofNullable(in).filter(s -> s != null).orElse("foo");
  }

  String removeAlwaysFalseCheckForConstantCondition(String in) {
    return Optional.ofNullable(in).filter(s -> s == null).orElse("foo");
  }

  String twoFiltersInARowGenerateOneIf(boolean b, String in) {
    return Optional.ofNullable(in).filter(s -> s.length() > 42).filter(s -> getIfTrue(s, b) != null).orElse("foo");
  }

  String twoFiltersInARowPrecedencePreserved(boolean a, boolean b, String in) {
    return Optional.ofNullable(in).filter(s -> a || b).orElse("foo");
  }

  private String getIfTrue(String str, boolean b) {
    return b ? str : null;
  }

}