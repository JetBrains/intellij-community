import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class Main {
  public Collection<? extends Number> get() {
    List<Number> lis<caret>t = IntStream.range(0, 100).boxed().collect(Collectors.toList());
    return list;
  }

}