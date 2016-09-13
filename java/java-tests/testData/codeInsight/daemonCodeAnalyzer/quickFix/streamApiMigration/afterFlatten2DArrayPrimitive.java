// "Replace with forEach" "true"
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Main {
  public List<Integer> test(int[][] arr) {
    List<Integer> result = new ArrayList<>();
      Arrays.stream(arr).filter(Objects::nonNull).forEach(subArr -> {
          for (int str : subArr) {
              result.add(str);
          }
      });
    return result;
  }
}