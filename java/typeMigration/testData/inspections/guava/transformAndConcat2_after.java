import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class A {
  void c() {
    ArrayList<String> strings = new ArrayList<String>();
    Stream<String> it = strings.stream();

    int i = it.flatMap((t) -> Collections.singletonList(t).stream()).collect(Collectors.toList()).size();

  }
}