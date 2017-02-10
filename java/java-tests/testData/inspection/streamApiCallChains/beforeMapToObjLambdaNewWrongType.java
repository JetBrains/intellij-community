// "Replace with 'boxed'" "false"

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class Main {
  public static void main(String[] args) {
    List<Double> list = LongStream.of(1,2,3,4).map<caret>ToObj((x) -> new Double(x)).collect(Collectors.toList());
  }
}