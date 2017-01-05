// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
  public void testForLoop() {
      List<Integer> result = IntStream.rangeClosed(0, 10).boxed().collect(Collectors.toList());
  }
}