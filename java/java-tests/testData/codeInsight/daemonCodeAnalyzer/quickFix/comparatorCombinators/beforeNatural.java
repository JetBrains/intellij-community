// "Replace with 'Comparator.naturalOrder'" "true-preview"

import java.util.*;

public class Main {
  Comparator<String> getComparator() {
    return (s1, s2) -> <caret>s1.compareTo(s2);
  }
}
