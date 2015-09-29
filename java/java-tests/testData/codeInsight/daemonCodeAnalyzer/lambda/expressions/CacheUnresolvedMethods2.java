
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

class Test {

  static class A {
    static class Row {
      public String get(int index) {
        return "test";
      }
    }

    void foo(List<Row> list) {
      list.stream().collect(Collectors.toMap(a -> String.valueOf(a.ge<caret>t(0)),
                                             a -> String.valueOf(a.get(1))));
    }

  }
}