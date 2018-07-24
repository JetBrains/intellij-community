// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  static String doProcess(String s) {
    return s;
  }

  public static void main(String[] args) {
      List<String> list = new ArrayList<>();
      for (String s: Arrays.asList("a", "b", "c")) {
          String doProcess = doProcess(s);
          list.add(doProcess);
      }
      System.out.println(list);
  }
}