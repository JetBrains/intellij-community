// "Replace with collect" "true"
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  public void test(Integer[] arr) {
      List<Integer> result = Arrays.stream(arr).filter(x -> x > 5).collect(Collectors.toList());
  }
}