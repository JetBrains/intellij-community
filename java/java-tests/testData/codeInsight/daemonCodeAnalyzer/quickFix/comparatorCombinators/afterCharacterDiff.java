// "Replace with 'Comparator.comparingInt'" "true-preview"

import java.util.*;

public class Main {
  static class Data {
    Short s;
    Byte b;
    Character c;
    Integer i;
    Long l;
    Double d;
  }

  void sort(List<Data> data) {
    data.sort(Comparator.comparingInt(d -> d.c));
  }
}
