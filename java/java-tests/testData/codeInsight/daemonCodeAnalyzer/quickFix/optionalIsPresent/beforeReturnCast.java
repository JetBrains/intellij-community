// "Replace Optional presence condition with functional style expression" "true-preview"

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Main {
  private static String test(List<Object> list) {
    Optional<Object> first = list.stream().filter(obj -> obj instanceof String).findFirst();
    if(first.isPre<caret>sent()) {
      return (String)first.get();
    }
    return null;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(null, null, "aa", "bbb", "c", null, "dd")));
  }
}