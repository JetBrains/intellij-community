// "Replace 'flatMap()' with 'flatMapToDouble()'" "true-preview"
import java.util.*;
import java.util.stream.*;

class Test {
  void test() {
    double[][] data = {{0,1,2},{3,4,5}};
    Arrays.stream(data).flatMapToDouble(array -> Arrays.stream(array)).forEach(System.out::println);
  }
}