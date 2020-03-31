// "Replace Stream API chain with loop" "true"

import java.util.StringJoiner;
import java.util.stream.*;

public class Test {
  private static final String ARRAY_ELEMENT_SEPARATOR = ", ", ARRAY_START = "[", ARRAY_END = "]";

  public static String nullSafeToString(byte[] array) {
      StringJoiner joiner = new StringJoiner(ARRAY_ELEMENT_SEPARATOR, ARRAY_START, ARRAY_END);
      for (int i = 0; i < array.length; i++) {
          String s = String.valueOf(array[i]);
          joiner.add(s);
      }
      return joiner.toString();
  }
}
