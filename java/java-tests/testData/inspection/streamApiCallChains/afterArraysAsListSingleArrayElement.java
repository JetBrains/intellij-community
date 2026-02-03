// "Replace Arrays.asList().stream() with Stream.of()" "true-preview"

import java.util.*;
import java.util.stream.Stream;

public class ArraysStreamSingleElementArray {
  Stream<String[]> stream(String[] args) {
    return Stream.<String[]>of(args);
  }
}