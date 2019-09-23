// "Fix all 'Optional can be replaced with sequence of if statements' problems in file" "true"

import java.util.*;

class Test {

  boolean isPresent(String in) {
      if (in == null) throw new NullPointerException();
      String s = in.substring(3);
      if (s.startsWith("1")) return true;
      return false;
  }

  boolean isEmpty(String in) {
      if (in == null) throw new NullPointerException();
      String s = in.substring(3);
      if (s.startsWith("1")) return false;
      return true;
  }

}