// "Remove 'comparingInt()' call and use 'thenComparingInt()'" "true-preview"
import java.util.*;

class Test {
  Comparator<String> cmp = Comparator.comparing(String::length).thenComparingInt(s -> s.charAt(1));
}