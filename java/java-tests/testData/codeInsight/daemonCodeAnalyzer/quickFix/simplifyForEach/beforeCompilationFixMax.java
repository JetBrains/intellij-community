// "Avoid mutation using Stream API 'max()' operation" "true-preview"

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
