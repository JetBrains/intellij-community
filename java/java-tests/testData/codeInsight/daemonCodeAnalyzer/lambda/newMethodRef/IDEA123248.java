import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

class App {

  void foo(Stream<Integer> boxed) {
    final Map<Integer, Integer> count = boxed.collect(HashMap::new, null, HashMap::putAll);
  }

}
