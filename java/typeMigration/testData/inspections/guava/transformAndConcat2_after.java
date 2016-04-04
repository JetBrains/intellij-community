import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Stream;

class A {
  void c() {
    ArrayList<String> strings = new ArrayList<String>();
    Stream<String> it = strings.stream();

    int i = (int) it.flatMap((t) -> Collections.singletonList(t).stream()).count();

  }
}