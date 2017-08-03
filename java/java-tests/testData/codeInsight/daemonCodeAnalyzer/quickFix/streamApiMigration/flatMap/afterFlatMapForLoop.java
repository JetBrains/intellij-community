// "Replace with findFirst()" "true"

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public class Main {
  public String testNestedForLoop(int[] data, List<String> info) {
      return Arrays.stream(data).flatMap(val -> IntStream.rangeClosed(0, val)).mapToObj(info::get).filter(str -> !str.isEmpty()).findFirst().orElse(null);
  }
}