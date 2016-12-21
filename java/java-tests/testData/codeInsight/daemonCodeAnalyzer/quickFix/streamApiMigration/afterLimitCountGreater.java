// "Replace with count()" "true"

import java.util.Arrays;

public class Main {
  public long test(String[] array, long limit) {
      long longStrings = Arrays.stream(array).map(String::trim).filter(trimmed -> trimmed.length() > 10).limit(limit + 1).count();
      return longStrings;
  }
}