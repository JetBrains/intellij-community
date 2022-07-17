// "Replace iteration with bulk 'Collection.addAll()' call" "true"
import java.util.ArrayList;
import java.util.List;

public class Main {
  public void test(Integer[] arr) {
    List<Integer> result = new ArrayList<>();
    result.add(1);
    for (int idx = 0; idx < arr.length; idx++) {
      result.<caret>add(arr[idx]);
    }
  }
}