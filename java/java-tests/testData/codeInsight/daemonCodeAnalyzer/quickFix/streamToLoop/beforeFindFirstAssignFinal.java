// "Replace Stream API chain with loop" "true"

import java.util.List;

public class Main {
  public void test(List<String> list) {
    // Cannot reuse the variable as user explicitly marked it as final
    final String res = list.stream().map(String::trim).filter(trim -> !trim.isEmpty()).fi<caret>ndFirst().orElse("");
    System.out.println(res);
  }
}
