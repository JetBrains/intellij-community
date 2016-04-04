import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class A {
  void c() {
    ArrayList<String> strings = new ArrayList<String>();
    Stream<String> it = strings.stream();

    List<Boolean> booleans = it.map(String::isEmpty).collect(Collectors.toList());

    boolean empty = it.map(s -> s.trim()).map(input -> input.toCharArray()).skip(777).filter(input -> input.length != 10).findAny().isPresent();
  }
}