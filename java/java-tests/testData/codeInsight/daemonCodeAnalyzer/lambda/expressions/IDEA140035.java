
import java.util.List;
import java.util.stream.Collectors;

class Test {

  static class Row {
    public String get(int index) {
      return "test";
    }
  }

  void foo(List<Row> list) {
    list.stream().collect(
      Collectors.toMap(a -> String.valueOf(a.get(0)), a -> String.valueOf(a.get(1))));
  }
}