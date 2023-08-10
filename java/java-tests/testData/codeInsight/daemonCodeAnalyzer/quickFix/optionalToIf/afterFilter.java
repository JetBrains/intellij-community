// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.*;

class Test {

  String checkNonConstantCondition(String in) {
      if (in != null && in.length() > 42) return in;
      return "foo";
  }

  String removeCheckForConstantCondition(String in) {
      if (in != null) return in;
      return "foo";
  }

  String removeAlwaysFalseCheckForConstantCondition(String in) {
      return "foo";
  }

  String twoFiltersInARowGenerateOneIf(boolean b, String in) {
      if (in != null && in.length() > 42 && getIfTrue(in, b) != null) return in;
      return "foo";
  }

  String twoFiltersInARowPrecedencePreserved(boolean a, boolean b, String in) {
      if (in != null && (a || b)) return in;
      return "foo";
  }

  private String getIfTrue(String str, boolean b) {
    return b ? str : null;
  }

}