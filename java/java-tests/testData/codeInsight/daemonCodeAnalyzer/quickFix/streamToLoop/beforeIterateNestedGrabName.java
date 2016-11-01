// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
  private static List<String> test() {
    return IntStream.range(0, 20).mapToObj(x -> x)
      .flatMap(x -> Stream.iterate("", str -> "a"+str).limit(x))
      .co<caret>llect(Collectors.toList());
  }

  public static void main(String[] args) {
    System.out.println(String.join("|", test()).length());
  }
}