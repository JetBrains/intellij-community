
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {
  public void foo(Stream<String> stream) {
    stream.collect(Collectors.toMap(null, e -> bar(e)));
  }

  public String bar(int entity) { return null; }
  public String bar(String entity) { return null; }
}