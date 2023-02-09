// "Collapse loop with stream 'forEach()'" "true-preview"
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class Sample {
  List<String> foo = new ArrayList<>();
  {
      foo.stream().filter(Objects::nonNull).filter(s -> s.startsWith("a")).forEach(System.out::println);
  }
}
