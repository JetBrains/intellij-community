// "Replace 'if' statement with 'Stream.ofNullable()' call" "true"

import java.util.*;
import java.util.stream.Stream;

class Other {
  Object val = new Object();

}

class Test {

  public Stream test(Other o) {
      return Stream.ofNullable(o.val);
  }
}