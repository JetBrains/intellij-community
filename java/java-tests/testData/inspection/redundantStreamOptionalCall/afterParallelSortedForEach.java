// "Replace the terminal operation with 'forEachOrdered()'" "true-preview"
import java.util.Arrays;
import java.util.List;

class X {
  void test() {
    List<Integer> list = Arrays.asList(5, 2, 22, 15, 100, 1);
    list.parallelStream()
      .sorted()
      .forEachOrdered(System.out::println);
  }
}