// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.*;

class Test {

  String reusesVariable(String in) {
      String s = null;
      if (in == null) throw new NullPointerException();
      String name = toName(in);
      if (name != null) s = name;
      if (s == null) {
          String defaultName = toDefaultName();
          s = defaultName;
      }
      return s;
  }

  String removesRedundantAssignment(String in) {
      String string = null;
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