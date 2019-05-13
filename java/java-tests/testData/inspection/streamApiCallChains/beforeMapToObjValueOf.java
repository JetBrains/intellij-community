// "Replace with 'boxed'" "true"

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
  public static void main(String[] args) {
    List<Integer> list = IntStream.range(0, 100).mapT<caret>oObj(Integer::valueOf).collect(Collectors.toList());
  }
}