// "Import static method 'java.util.stream.Collectors.toList()'" "true-preview"

import java.util.stream.Stream;

public class X {

  {
    System.out.println(Stream.of(123, 456)
                         .map(i -> i + 1)
                         .collect(toLi<caret>st()));
  }
}

class Y {
  private static void toList() {}
}