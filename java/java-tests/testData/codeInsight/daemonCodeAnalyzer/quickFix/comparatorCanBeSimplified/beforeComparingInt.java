// "Remove 'comparingInt()' call and use 'thenComparingInt()'" "true-preview"
import java.util.*;

class Test {
  Comparator<String> cmp = Comparator.comparing(String::length).thenComparing(Comparator.c<caret>omparingInt(s -> s.charAt(1)));
}