import com.google.common.base.Function;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

class A {
  void c() {
    ArrayList<String> strings = new ArrayList<String>();
    Stream<String> it = strings.stream();

    int i = (int) it.flatMap((f) -> getFunction().apply(f).stream()).count();
  }

  Function<String, List<String>> getFunction() {
    return null;
  }

}