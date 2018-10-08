// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static void test(List<String> list) {
    while(list.stream().filter(x -> x != null).an<caret>yMatch(x -> x.startsWith("x"))) {
      list = process(list);
    }
  }

  private static void test2(List<String> list) {
    while(list.size() > 2 && list.stream().filter(x -> x != null).anyMatch(x -> x.startsWith("x"))) {
      list = process(list);
    }
  }

  private static void test3(List<String> list) {
    while(list.stream().filter(x -> x != null).anyMatch(x -> x.startsWith("x")) && list.size() > 2) {
      list = process(list);
    }
  }

  private static void test4(List<String> list) {
    while(list.size() > 2 && list.stream().filter(x -> x != null).filter(x -> x.startsWith("x")).count() > 10 && list.size() < 10) {
      list = process(list);
    }
  }

  native List<String> process(List<String> list);
}