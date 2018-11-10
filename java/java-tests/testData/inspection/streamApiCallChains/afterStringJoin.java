// "Replace with 'Collectors.joining'" "true"

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

class Test {
  public void test() {
      /*2*/
      /*1*/
      Stream.of(12, 22)
        .map(Object::toString) /*3*/
        .collect(((Collectors.joining("\n"))) // comment?
       );

  }
}