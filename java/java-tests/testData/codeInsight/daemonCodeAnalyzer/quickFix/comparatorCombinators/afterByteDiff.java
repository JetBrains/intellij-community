// "Replace with Comparator.comparingInt" "true"

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
    data.sort(Comparator.comparingInt(d -> d.b));
  }
}
