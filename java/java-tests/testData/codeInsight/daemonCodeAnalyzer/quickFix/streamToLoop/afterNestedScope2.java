// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.function.Consumer;

import static java.util.Arrays.asList;

public class Main {
  public static long test(List<String> list) {
      long count = 0L;
      for (String l : list) {
          if (l != null) {
              (new Consumer<String>() {
                  String list = "hello";

                  public void accept(String list) {
                      System.out.println(this.list + l);
                  }
              }).accept(l);
              count++;
          }
      }
      return count;
  }

  public static void main(String[] args) {
    System.out.println(test(asList("a", "b", "c")));
  }
}