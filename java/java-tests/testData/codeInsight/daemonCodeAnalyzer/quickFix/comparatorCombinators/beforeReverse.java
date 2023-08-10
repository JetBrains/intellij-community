// "Replace with 'Comparator.reverseOrder'" "true-preview"

import java.util.*;

public class Main {
  Comparator<String> getComparator() {
    return (s1, s2) -> <caret>s2.compareTo(s1);
  }
}
