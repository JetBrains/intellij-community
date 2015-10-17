import java.util.*;

class Foo {
  Optional<Integer> foo(Object o) {
    return o == null ? Optional.empty()<caret>
  }
}