// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static void test(List<String> list) {
    if(list.stream().filter(x -> x != null).an<caret>yMatch(x -> x.startsWith("x"))) {
      System.out.println("Ok!");
    }
    if(list.size() > 2 && list.stream().filter(x -> x != null).anyMatch(x -> x.startsWith("x"))) {
      System.out.println("Ok!");
    }
    if(list.size() > 2 && list.stream().filter(x -> x != null).anyMatch(x -> x.startsWith("x")) && list.size() < 10) {
      System.out.println("Ok!");
    }
    if(list.size() > 2 || list.stream().filter(x -> x != null).anyMatch(x -> x.startsWith("x"))) { // not supported
      System.out.println("Ok!");
    }
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "b", "xyz"));
  }
}