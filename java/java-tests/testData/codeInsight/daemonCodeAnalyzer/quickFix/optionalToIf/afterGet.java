// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.*;

class Test {

  String exceptionIsThrownIfNull(String in) {
      if (in == null || in.length() <= 2) throw new NoSuchElementException("No value present");
      return in;
  }

  void ofNullableGetFinalVar(String in) {
      if (in == null) throw new NoSuchElementException("No value present");
      final String out = in;
      Runnable r = () -> java.lang.System.out.println(out);
  }

}