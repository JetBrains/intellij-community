// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.function.Consumer;

import static java.util.Arrays.asList;

public class Main {
  public static long test(List<String> list) {
    return list.stream().filter(l -> l != null).peek(lst -> (new Consumer<String>() {
      String lst = "hello";
      public void accept(String lst) {System.out.println(this.lst+ lst);}
    }).accept(lst)).co<caret>unt();
  }

  public static void main(String[] args) {
    System.out.println(test(asList("a", "b", "c")));
  }
}