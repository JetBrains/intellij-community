import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Stream;

class A {
  void c() {
    Stream<String> it = new ArrayList<String>().stream();
    int i = (int) it.flatMap(input -> Collections.emptyList().stream()).count();
  }
}