// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  private static void getMap(List<String> strings) {
      List<Integer> list = new ArrayList<>();
      for (String string: strings) {
          Integer len = string.length();
          Integer integer = len * 2;
          list.add(integer);
      }
      System.out.println(list);
  }

  public static void main(String[] args) {
    getMap(Arrays.asList("a", "bbb", "cccc", "dddd", "ee", "e"));
  }
}
