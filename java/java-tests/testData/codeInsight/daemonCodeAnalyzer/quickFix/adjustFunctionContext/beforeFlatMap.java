// "Replace 'flatMap()' with 'flatMapToDouble()'" "true"
import java.util.*;
import java.util.stream.*;

class Test {
  void test() {
    double[][] data = {{0,1,2},{3,4,5}};
    Arrays.stream(data).flatMap(array -> Arrays.<caret>stream(array)).forEach(System.out::println);
  }
}