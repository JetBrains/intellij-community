// "Replace iteration with bulk 'Collection.addAll()' call" "true-preview"
import java.util.*;

public class Main {
  public void test(Integer[] arr) {
    List<Integer> result = new ArrayList<>();
    result.add(1);
    Arrays.stream(arr).forEach(result<caret>::add);
  }
}