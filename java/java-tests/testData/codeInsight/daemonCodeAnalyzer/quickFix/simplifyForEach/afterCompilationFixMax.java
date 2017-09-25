// "Avoid mutation using Stream API 'max()' operation" "true"

import java.util.*;

public class Main {
  void test(List<String> list) {
      String longest = list.stream().max(Comparator.comparingInt(String::length)).orElse(null);
      System.out.println(longest);
  }
}
