// "Collapse loop with stream 'count()'" "true-preview"

import java.util.Arrays;

public class Main {
  public long test(String[] array) {
    long longStrings = Arrays.stream(array).map(String::trim).filter(trimmed -> trimmed.length() > 10).limit(100).count();
      return longStrings;
  }
}