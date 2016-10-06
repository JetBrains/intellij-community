// "Replace with collect" "true"
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Main {
  public List<Integer> test(int[][] arr) {
      List<Integer> result = Arrays.stream(arr).filter(Objects::nonNull).flatMapToInt(Arrays::stream).boxed().collect(Collectors.toList());
      return result;
  }
}