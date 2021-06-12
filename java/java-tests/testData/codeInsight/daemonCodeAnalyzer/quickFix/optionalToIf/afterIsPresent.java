// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.*;

class Test {

  boolean isPresent(String in) {
      if (in == null) throw new NullPointerException();
      String s = in.substring(3);
      if (s.startsWith("1")) return true;
      return false;
  }

  boolean isPresentFinalVariable(String in) {
      String result = false;
      if (in != null) result = true;
      @Deprecated final String isPresent = result;
      Runnable r = () -> System.out.println(isPresent);
  }

  boolean isPresentCanBeNonFinalVariable(String in) {
      var isPresent = false;
      if (in != null) isPresent = true;
      System.out.println(isPresent);
  }

  boolean isEmpty(String in) {
      if (in == null) throw new NullPointerException();
      String s = in.substring(3);
      if (s.startsWith("1")) return false;
      return true;
  }

}