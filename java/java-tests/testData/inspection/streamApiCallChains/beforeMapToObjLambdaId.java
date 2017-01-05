// "Replace with 'boxed'" "true"

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class Main {
  public static void main(String[] args) {
    List<Integer> list = LongStream.range(0, 100).ma<caret>pToObj(x -> x).collect(Collectors.toList());
  }
}