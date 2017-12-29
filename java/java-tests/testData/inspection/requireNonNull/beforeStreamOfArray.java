// "Replace condition with Stream.ofNullable" "false"

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
  Stream<Object> getStream(Object[] arr) {
    if<caret>(arr == null) return Stream.empty();
    else return Stream.of(arr);
  }
}
