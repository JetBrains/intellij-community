// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
  public static IntSummaryStatistics test() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      long limit = 20;
      for (String x = ""; ; x = x + "a") {
          if (limit-- == 0) break;
          int length = x.length();
          stat.accept(length);
      }
      return stat;
  }

  public static List<String> testUseName() {
      /*limit*/
      List<String> list = new ArrayList<>();
      long limit = 20;
      for (String x = ""; ; x = x /* add "a" */ + "a") {
          if (limit-- == 0) break;
          list.add(x);
      }
      return list;
  }

  public static IntSummaryStatistics testNested() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      for (int limit = 0; limit < 20; limit++) {
          long limitInner = limit;
          for (String x = ""; ; x = x + limit) {
              if (limitInner-- == 0) break;
              int length = x.length();
              stat.accept(length);
          }
      }
      return stat;
  }

  private static List<String> testNestedUseName() {
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

  public static IntSummaryStatistics testNestedRename() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      for (int x = 0; x < 20; x++) {
          if (x > 2) {
              long limit = x;
              for (String s = String.valueOf(x); ; s = s + x) {
                  if (limit-- == 0) break;
                  int length = s.length();
                  stat.accept(length);
              }
          }
      }
      return stat;
  }

  static void print(Supplier<String> messageSupplier) {
    System.out.println(messageSupplier.get());
  }

  public static void main(String[] args) {
    System.out.println(test());
    System.out.println(testUseName());
    System.out.println(testNested());
    System.out.println(testNestedRename());
    System.out.println(String.join("|", testNestedUseName()).length());

      // convert to loop
      for (int j = 1; j < 2; j++) {
          int x1 = j;
          if (x1 > 0) {
              print(() -> "attempt #" + x1);
          }
      }

      // convert to loop
      for (int x = 1; x < 2; x++) {
          if (x > 0) {
              int i = x + 1;
              print(() -> "attempt #" + i);
          }
      }
  }
}