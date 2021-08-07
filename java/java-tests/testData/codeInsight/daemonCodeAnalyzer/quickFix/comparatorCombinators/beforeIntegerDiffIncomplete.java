// "Replace with 'Comparator.comparingInt'" "false"

import java.util.*;

public class Main {
  void sort(List<String> data) {
    data.sort((d1, d2) -> d1.le<caret>ngth() - );
  }
}
