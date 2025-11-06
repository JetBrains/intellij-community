import java.util.List;

import static java.time.temporal.ChronoField.values;


class Foo {
  void test(List<String> baz) {
    values()<caret>
  }
}