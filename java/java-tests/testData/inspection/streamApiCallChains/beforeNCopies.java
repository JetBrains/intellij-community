// "Replace with Stream.generate()" "true"

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class Test {
  public void test(Object[] array) {
    Collections.nCopies(10, "").<caret>stream().map(x -> new Object());
  }
}