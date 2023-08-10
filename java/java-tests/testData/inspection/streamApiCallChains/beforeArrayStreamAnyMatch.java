// "Replace with 'List.contains()'" "true-preview"

import java.util.Arrays;

public class Main {
  public boolean find(String[] data, String key) {
    return Arrays.stream(data).anyMat<caret>ch(key::equals);
  }
}