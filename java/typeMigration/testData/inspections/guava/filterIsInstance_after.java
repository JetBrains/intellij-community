import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class A {
  void c() {
    ArrayList<String> strings = new ArrayList<String>();
    Stream<String> it = strings.stream();

    int i = it.filter(String::isEmpty).filter(String.class::isInstance).collect(Collectors.toList()).size();
  }
}