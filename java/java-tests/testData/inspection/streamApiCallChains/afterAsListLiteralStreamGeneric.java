// "Replace Arrays.asList().stream() with Stream.of()" "true"

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
  public static void main(String[] args) {
    List<List<Object>> list = Stream.<List<Object>>of(Arrays.asList(1,2,3), Arrays.asList(1.0, 2.0, 3.0))
      .collect(Collectors.toList());
  }
}
