// "Fix all 'Optional can be replaced with sequence of if statements' problems in file" "true"

import java.util.*;

class Test {

  boolean isPresent(String in) {
    return Optional.of<caret>(in).map(in -> in.substring(3)).filter(s -> s.startsWith("1")).isPresent();
  }

  boolean isEmpty(String in) {
    return Optional.of(in).map(in -> in.substring(3)).filter(s -> s.startsWith("1")).isEmpty();
  }

}