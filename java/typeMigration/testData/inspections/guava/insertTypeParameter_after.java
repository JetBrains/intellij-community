import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class A {
  void c() {
    Stream<String> it = new ArrayList<String>().stream();
    int i = it.flatMap(input -> Collections.emptyList().stream()).collect(Collectors.toList()).size();
  }
}