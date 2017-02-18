// "Replace with Comparator.comparingDouble" "true"

import java.util.*;

public class Main {
  static class Data {
    short s;
    byte b;
    char c;
    int i;
    long l;
    double d;
  }

  void sort(List<Data> data) {
    data.sort((d1, d2) -> d1.d - d2.<caret>d);
  }
}
