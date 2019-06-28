// "Replace Stream API chain with loop" "true"

import java.util.stream.*;

public class Test {
  private static final String ARRAY_ELEMENT_SEPARATOR = ", ", ARRAY_START = "[", ARRAY_END = "]";

  public static String nullSafeToString(byte[] array) {
    return IntStream.range(0, array.length).mapToObj(i -> String.valueOf(array[i]))
      .collec<caret>t(Collectors.joining(ARRAY_ELEMENT_SEPARATOR, ARRAY_START, ARRAY_END));
  }
}
