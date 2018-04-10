// "Remove 'comparing()' call" "true"
import java.util.*;

class Test {
  Comparator<String> cmp = Comparator.comparing(String::length).thenComparing(Comparator.c<caret>omparing(s -> s.charAt(1)));
}