import java.util.List;

import static java.time.temporal.ChronoField.NANO_OF_SECOND;


class Foo {
  void test(List<String> baz) {
    NANO_OF_SECOND<caret>
  }
}