// "Replace with collect" "false"

import java.util.List;

public class Test {
  public static void test(List<String> list) {
    StringBuilder sb = new StringBuilder();
    if(!list.isEmpty()) {
      for<caret> (String s : list) {
        sb.append(s);
      }
    }
    Runnable r = () -> System.out.println(sb.toString());
    r.run();
  }
}