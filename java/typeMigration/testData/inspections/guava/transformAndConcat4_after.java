import com.google.common.base.Function;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class A {
  void c() {
    ArrayList<String> strings = new ArrayList<String>();
    Stream<String> it = strings.stream();

    int i = it.flatMap((f) -> getFunction().apply(f).stream()).collect(Collectors.toList()).size();
  }

  Function<String, List<String>> getFunction() {
    return null;
  }

}