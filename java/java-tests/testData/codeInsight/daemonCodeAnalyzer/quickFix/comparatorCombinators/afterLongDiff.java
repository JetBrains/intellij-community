// "Replace with 'Comparator.comparingLong'" "true-preview"

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
    data.sort(Comparator.comparingLong(d -> d.l));
  }
}
