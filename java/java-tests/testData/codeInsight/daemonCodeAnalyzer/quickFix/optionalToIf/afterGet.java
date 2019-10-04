// "Fix all 'Optional can be replaced with sequence of if statements' problems in file" "true"

import java.util.*;

class Test {

  String exceptionIsThrownIfNull(String in) {
      if (in == null || in.length() <= 2) throw new NoSuchElementException("No value present");
      return in;
  }

}