// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
  public void testForLoop(List<String> input) {
      List<Integer> result = IntStream.range(0, 10).mapToObj(i -> input.get(i).length()).collect(Collectors.toList());
  }
}