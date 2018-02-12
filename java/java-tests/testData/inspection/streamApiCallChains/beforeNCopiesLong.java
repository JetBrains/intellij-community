// "Replace with Stream.generate()" "true"

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class Test {
  public void test() {
    Collections.nCopies(10, "").<caret>stream().mapToLong(x -> 42l).filter(x -> x > 12);
  }
}