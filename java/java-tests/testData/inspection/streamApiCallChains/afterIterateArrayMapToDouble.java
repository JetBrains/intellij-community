// "Replace IntStream.range().mapToDouble() with Arrays.stream(numbers)" "true"

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public class Test extends ArrayList<String> {
  public void test(List<List<String>> list) {
    double[] numbers = {1,2,3,4,5};
    double[] doubled = Arrays.stream(numbers).map(number -> number * 2).toArray();
  }
}
