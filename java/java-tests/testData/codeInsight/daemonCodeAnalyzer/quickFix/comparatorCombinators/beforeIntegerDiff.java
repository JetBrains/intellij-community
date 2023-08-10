// "Replace with 'Comparator.comparingInt'" "true-preview"

import java.util.*;

public class Main {
  void sort(List<String> data) {
    data.sort((d1, d2) -> d1.length() - d2.le<caret>ngth());
  }
}
