// "Fix all 'Optional can be replaced with sequence of if statements' problems in file" "true"

import java.util.Optional;
import java.util.stream.Stream;

class Test {

  void stream(String in) {
    Stream<String> out = Optional.<caret>of(in).map(s -> s.length() > 2 ? s : null).stream();
  }

}