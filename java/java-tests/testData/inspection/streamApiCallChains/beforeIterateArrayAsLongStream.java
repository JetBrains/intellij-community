// "Replace 'IntStream.range().mapToLong()' with 'Arrays.stream(intArr)'" "true-preview"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class Test extends ArrayList<String> {
  public void test(List<List<String>> list) {
    int[] intArr = {1,2,3,4,5};
    long[] longs = IntStream.range(0, intArr.length).map<caret>ToLong(idx -> intArr[idx]).toArray();
  }
}
