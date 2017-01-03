// "Replace Stream API chain with loop" "true"

import java.util.List;

public class Main {
  public void test(List<String> list) {
      String res = "";
      for (String s : list) {
          String trim = s.trim();
          if (!trim.isEmpty()) {
              res = trim;
              break;
          }
      }
      System.out.println(res);
  }
}
