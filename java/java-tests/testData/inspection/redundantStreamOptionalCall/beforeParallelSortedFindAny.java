// "Replace the terminal operation with 'findFirst()'" "true-preview"
import java.util.Arrays;
import java.util.List;

class X {
  void test() {
    List<Integer> list = Arrays.asList(5, 2, 22, 15, 100, 1);
    int value = list.parallelStream()
      .<caret>sorted()
      .filter(x -> x % 2 == 0)
      .findAny().get();
  }
}