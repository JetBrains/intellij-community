// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.Objects;

public class Main {
  boolean test(String[] strings) {
      for (String s : strings) {
          if (s != null) {
              if (!s.startsWith("xyz")) {
                  return true;
              }
          }
      }
      return false;
  }
}
