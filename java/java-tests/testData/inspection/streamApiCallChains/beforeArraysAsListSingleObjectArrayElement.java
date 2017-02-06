// "Replace Arrays.asList().stream() with Stream.of()" "true"

import java.util.*;
import java.util.stream.Stream;

public class ArraysStreamSingleObjectElementArray {
  Stream<Object[]> stream(String[] args) {
    return Arrays.<Object[]>asList(args).stre<caret>am();
  }
}