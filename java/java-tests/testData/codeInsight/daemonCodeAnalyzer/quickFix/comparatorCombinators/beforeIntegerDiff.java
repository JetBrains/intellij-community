// "Replace with Comparator.comparingInt" "true"

import java.util.*;

public class Main {
  void sort(List<String> data) {
    data.sort((d1, d2) -> d1.length() - d2.le<caret>ngth());
  }
}
