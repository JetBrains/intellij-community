// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.stream.*;

class X {
  record N(N parent, List<N> children, String whatever) {}
  
  private static N reproducer(N parent, String frame) {
      for (N child : parent.children) {
          if (child.whatever.equals(frame)) {
              return child;
          }
      }
      N result = new N(parent, new ArrayList<>(), frame);
      parent.children.add(result);
      return result;
  }

  void testMap(List<String> list) {
      List<List<String>> newList = new ArrayList<>();
      for (String s : list) {
          List<String> result = new ArrayList<>();
          result.add(s);
          result.add(s + s);
          List<String> apply = result;
          newList.add(apply);
      }
  }

  void testFilter(List<String> list) {
      List<String> newList = new ArrayList<>();
      for (String string : list) {
          boolean result = false;
          if (string.isEmpty()) result = true;
          if (result) {
              newList.add(string);
          }
      }
  }

  private static List<String> getStrings(List<String> test) {
      ArrayList<String> objects1 = new ArrayList<>();
      objects1.add("1");
      ArrayList<String> strings = objects1;
      for (String string : test) {
          System.out.println("1");
          strings.add(string);
      }
      return strings;
  }
}