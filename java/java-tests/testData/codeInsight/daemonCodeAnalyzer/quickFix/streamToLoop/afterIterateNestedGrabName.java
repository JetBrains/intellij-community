// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
  private static List<String> test() {
      List<String> list = new ArrayList<>();
      for (int x = 0; x < 20; x++) {
          Integer integer = x;
          long limit = integer;
          for (String str = ""; ; str = "a" + str) {
              if (limit-- == 0) break;
              list.add(str);
          }
      }
      return list;
  }

  public static void main(String[] args) {
    System.out.println(String.join("|", test()).length());
  }
}