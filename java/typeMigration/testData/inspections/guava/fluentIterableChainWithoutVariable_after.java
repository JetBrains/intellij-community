import java.util.ArrayList;
import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Main {
  void mmm() {
    Iterator<String> iterator = m().iterator();
    int i = m1() + 10;
    Stream<String> strings = m2();
  }

  Iterable<String> m() {
    return new ArrayList<String>().stream().map(s -> s + s).filter(String::isEmpty).collect(Collectors.toList());
  }

  int m1() {
    return (int) new ArrayList<String>().stream().map(s -> s + s).count();
  }

  Stream<String> m2() {
    return new ArrayList<String>().stream().map(s -> s + s).filter(String::isEmpty);
  }

}