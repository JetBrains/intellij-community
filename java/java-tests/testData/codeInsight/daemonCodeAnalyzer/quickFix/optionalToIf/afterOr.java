// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.*;

class Test {

  String reusesVariable(String in) {
      String s1 = null;
      if (in == null) throw new NullPointerException();
      String s = toName(in);
      if (s != null) s1 = s;
      if (s1 == null) {
          String value = toDefaultName();
          s1 = value;
      }
      return s1;
  }

  String removesRedundantAssignment(String in) {
      String s1 = null;
      String s = null;
      if (in == null) throw new NullPointerException();
      s = in;
      return s;
  }

  private String toName(String str) {
    if (str.startsWith("name")) return str.substring(4);
    return null;
  }

  private String toDefaultName() {
    return "defaultName";
  }

}