// "Replace 'flatMap()' with 'flatMapToInt()'" "true-preview"
import java.util.*;
import java.util.stream.*;

class Test {
  void test() {
    int[][] data = {{0,1,2},{3,4,5}};
    Arrays.stream(data).flatMapToInt(Arrays::stream).forEach(System.out::println);
  }
}