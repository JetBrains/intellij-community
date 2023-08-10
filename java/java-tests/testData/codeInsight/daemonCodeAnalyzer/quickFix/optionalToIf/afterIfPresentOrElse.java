// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.*;

class Test {

  void inStatement(String in) {
      String value = null;
      if (in != null && in.length > 2) {
          String ss = in.substring(3);
          String strOrNull = getStrOrNull(ss);
          if (strOrNull != null) value = strOrNull;
      }
      if (value == null) {
          System.out.println("value is null");
      } else {
          System.out.println("found value %s", value);
      }
  }


  private String getStrOrNull(String s) {
    return s.length() > 2 ? s : null;
  }

}