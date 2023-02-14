// "Replace Stream API chain with loop" "true-preview"

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Main {
  private static void test(List<String> list) {
    String result = list.stream().filter(Objects::nonNull).coll<caret>ect(StringBuilder::new, (sb, str) -> sb.append(str), StringBuilder::append).toString();
    System.out.println(result);
  }

  public static void main(String[] args) {
    test(Arrays.asList("aa", "bbb", "c", null, "dd"));
  }
}