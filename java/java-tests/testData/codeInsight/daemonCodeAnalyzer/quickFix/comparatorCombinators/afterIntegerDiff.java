// "Replace with Comparator.comparingInt" "true"

import java.util.*;

public class Main {
  void sort(List<String> data) {
    data.sort(Comparator.comparingInt(String::length));
  }
}
