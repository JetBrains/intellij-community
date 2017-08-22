// "Replace with anyMatch()" "true"

import java.util.Arrays;
import java.util.Objects;

public class Main {
  void contains(String[][] haystack, String needle) {
    if(haystack != null) {
        if (Arrays.stream(haystack).filter(Objects::nonNull).flatMap(Arrays::stream).anyMatch(needle::equals)) {
            return;
        }
    }
    throw new IllegalStateException();
  }
}
