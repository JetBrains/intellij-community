// "Replace with count()" "true"

import java.util.Arrays;

public class Main {
  public long test(String[] array) {
      long longStrings = Arrays.stream(array).map(String::trim).filter(trimmed -> trimmed.length() > 10).limit(102).count();
      return longStrings;
  }
}