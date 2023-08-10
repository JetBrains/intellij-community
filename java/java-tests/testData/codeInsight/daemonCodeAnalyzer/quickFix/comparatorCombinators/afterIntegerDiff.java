// "Replace with 'Comparator.comparingInt'" "true-preview"

import java.util.*;

public class Main {
  void sort(List<String> data) {
    data.sort(Comparator.comparingInt(String::length));
  }
}
