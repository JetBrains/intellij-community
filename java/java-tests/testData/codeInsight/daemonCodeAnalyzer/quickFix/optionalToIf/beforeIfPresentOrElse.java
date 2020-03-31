// "Fix all 'Optional can be replaced with sequence of if statements' problems in file" "true"

import java.util.*;

class Test {

  void inStatement(String in) {
    Optional.ofNullable<caret>(in).filter(s -> s.length > 2).map(s -> s.substring(3)).map(ss -> getStrOrNull(ss))
      .ifPresentOrElse(value -> System.out.println("found value %s", value),
                       () -> System.out.println("value is null"));
  }


  private String getStrOrNull(String s) {
    return s.length() > 2 ? s : null;
  }

}