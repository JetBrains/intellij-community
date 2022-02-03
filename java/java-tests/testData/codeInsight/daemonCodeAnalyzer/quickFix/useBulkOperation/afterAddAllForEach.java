// "Replace iteration with bulk 'Collection.addAll()' call" "true"
import java.util.*;

public class Main {
  public void test(Integer[] arr) {
    List<Integer> result = new ArrayList<>();
    result.add(1);
      result.addAll(Arrays.asList(arr));
  }
}