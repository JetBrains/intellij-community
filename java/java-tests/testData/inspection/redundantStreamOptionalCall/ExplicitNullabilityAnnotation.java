import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

@NotNullByDefault
class Test {
  private static void processList(List<Integer> list) {
    Integer result = list.stream()
      .<@Nullable Integer>map(x -> x) // We want to prevent the 'redundant map operation' warning on this line
      .reduce(null, (a, b) -> a == null ? b : Math.max(a, b));
    if (result != null) {
      System.out.println(result);
    }
    else {
      System.out.println("empty");
    }
  }

  public static void main(String[] args) {
    processList(Arrays.asList(1, 2, 3, 4, 5, 1, 2));
  }
}