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
                  String lst = "hello";

                  public void accept(String lst) {
                      System.out.println(this.lst + lst);
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