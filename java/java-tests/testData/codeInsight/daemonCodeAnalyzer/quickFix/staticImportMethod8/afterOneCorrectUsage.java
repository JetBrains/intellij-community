// "Import static method 'java.util.stream.Collectors.toList'" "true"

import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class X {

  {
    System.out.println(Stream.of(123, 456)
                         .map(i -> i + 1)
                         .collect(toList()));
  }
}

class Y {
  private static void toList() {}
}