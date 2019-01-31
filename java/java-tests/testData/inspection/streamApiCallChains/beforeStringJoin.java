// "Replace with 'Collectors.joining'" "true"

import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

class Test {
  public void test() {
    String.join(/*2*/"\n" /*1*/,
                (Stream.of(12, 22)
                  .map(Object::toString) /*3*/
                  .collect<caret>(((toList())) // comment?
                 ))
    );

  }
}