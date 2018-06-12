// "Replace with Arrays.asList().contains()" "true"

import java.util.Arrays;

public class Main {
  public boolean find(String[] data, String key) {
    return Arrays.asList(data).contains(key);
  }
}