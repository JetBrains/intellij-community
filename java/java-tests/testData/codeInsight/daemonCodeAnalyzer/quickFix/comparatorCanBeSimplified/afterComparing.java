// "Remove 'comparing()' call" "true"
import java.util.*;

class Test {
  Comparator<String> cmp = Comparator.comparing(String::length).thenComparing(s -> s.charAt(1));
}