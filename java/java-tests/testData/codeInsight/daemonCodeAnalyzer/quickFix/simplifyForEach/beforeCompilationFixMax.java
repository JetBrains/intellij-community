// "Avoid mutation using Stream API 'max()' operation" "true"

import java.util.*;

public class Main {
  void test(List<String> list) {
    String longest = null;
    list.forEach(s -> {
      if(longest == null || longest.length() < s.length()) longe<caret>st = s;
    });
    System.out.println(longest);
  }
}
