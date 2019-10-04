// "Fix all 'Optional can be replaced with sequence of if statements' problems in file" "true"

import java.util.*;

class Test {

  String exceptionIsThrownIfNull(String in) {
    return Optional.ofNullable<caret>(in).filter(s -> s.length() > 2).get();
  }

}