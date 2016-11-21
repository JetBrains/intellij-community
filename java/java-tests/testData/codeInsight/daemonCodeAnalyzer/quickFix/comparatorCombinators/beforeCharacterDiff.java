// "Replace with Comparator.comparingInt" "true"

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
    data.sort((d1, d2) -> d1.<caret>c - d2.c);
  }
}
