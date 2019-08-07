// "Fix all 'Optional can be replaced with sequence of if statements' problems in file" "true"

import java.util.*;

class Test {

  void statementWithGetGeneratesThrowStatement() {
      Object empty = null;
      throw new NoSuchElementException("No value present");
  }
}