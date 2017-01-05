// "Replace Stream API chain with loop" "true"

import java.util.List;

public class Main {
  public void test(List<String> list) {
    long x = list.stream().coun<caret>t();
    System.out.println(x);
  }
}