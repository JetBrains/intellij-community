// "Replace with anyMatch()" "true"

import java.util.Arrays;
import java.util.Objects;

public class Main {
  String contains(String[][] haystack, String needle) {
    if(haystack != null) {
        if (Arrays.stream(haystack).filter(Objects::nonNull).flatMap(Arrays::stream).anyMatch(needle::equals)) {
            return "yes" + needle.length();
        }
      System.out.println("oops");
    }
    return "no";
  }
}
