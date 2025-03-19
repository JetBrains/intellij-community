// "Replace Stream API chain with loop" "true-preview"

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TemplateTest {
  void test() {
      List<Integer> list = new ArrayList<>();
      for (Integer i : Arrays.asList(1, 2, 3)) {
          list.add(i);
      }
      System.out.println(STR."\{list}");
  }
}