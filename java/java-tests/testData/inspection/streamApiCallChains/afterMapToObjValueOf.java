// "Replace with 'boxed'" "true-preview"

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
  public static void main(String[] args) {
    List<Integer> list = IntStream.range(0, 100).boxed().collect(Collectors.toList());
  }
}