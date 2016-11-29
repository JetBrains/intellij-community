// "Replace with anyMatch()" "true"

import java.util.Arrays;
import java.util.Objects;

public class Main {
  boolean contains(String[][] haystack, String needle) {
    if(haystack != null)
        return Arrays.stream(haystack).filter(Objects::nonNull).flatMap(Arrays::stream).anyMatch(needle::equals);
    return false;
  }
}
