// "Replace with Stream.generate()" "true"

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class Test {
  public void test() {
    Collections.nCopies(10, "").<caret>stream().mapToInt(x -> 42).filter(x -> x > 12);
  }
}