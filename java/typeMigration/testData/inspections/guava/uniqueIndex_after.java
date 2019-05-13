import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class X {

  void m(Stream<String> it, Function<String, String> f) {
    Map<String, String> index = it.collect(Collectors.toMap(Function.identity(), f));
    System.out.println(index.get("asd"));
  }
}