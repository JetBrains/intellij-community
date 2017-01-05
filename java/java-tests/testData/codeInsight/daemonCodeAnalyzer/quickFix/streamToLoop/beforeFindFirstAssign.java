// "Replace Stream API chain with loop" "true"

import java.util.List;

public class Main {
  public void test(List<String> list) {
    String res = list.stream().map(String::trim).filter(trim -> !trim.isEmpty()).fi<caret>ndFirst().orElse("");
    System.out.println(res);
  }
}
