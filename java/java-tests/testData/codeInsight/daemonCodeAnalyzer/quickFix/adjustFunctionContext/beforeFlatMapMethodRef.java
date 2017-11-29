// "Replace 'flatMap()' with 'flatMapToInt()'" "true"
import java.util.*;
import java.util.stream.*;

class Test {
  void test() {
    int[][] data = {{0,1,2},{3,4,5}};
    Arrays.stream(data).flatMap(Arrays<caret>::stream).forEach(System.out::println);
  }
}