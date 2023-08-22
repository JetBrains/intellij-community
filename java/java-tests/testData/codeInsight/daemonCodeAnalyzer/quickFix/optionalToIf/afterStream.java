// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.Optional;
import java.util.stream.Stream;

class Test {

  void stream(String in) {
      Stream<String> out = Stream.empty();
      if (in == null) throw new NullPointerException();
      String s = in.length() > 2 ? in : null;
      if (s != null) out = Stream.of(s);
  }

}