// "Replace Stream API chain with loop" "true-preview"

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
  public void test(List<String> list) {
      List<String> result = new ArrayList<>();
      Function<? super String, ? extends String> function = list.size() < 10 ? String::trim : Function.identity();
      for (String s : list) {
          String string = function.apply(s);
          result.add(string);
      }
      System.out.println(result);
  }
}
