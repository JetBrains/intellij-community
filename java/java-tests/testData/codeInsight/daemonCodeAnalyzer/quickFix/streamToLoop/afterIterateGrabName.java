// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.List;

public class Main {
  public static List<String> test() {
      List<String> list = new ArrayList<>();
      long limit = 20;
      for (String x = ""; ; x = x + "a") {
          if (limit-- == 0) break;
          list.add(x);
      }
      return list;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}