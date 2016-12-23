// "Replace Stream API chain with loop" "true"

import java.util.List;

public class Main {
  public void test(List<String> list) {
    // Cannot reuse the variable as user explicitly marked it as final
      String found = "";
      for (String s : list) {
          String trim = s.trim();
          if (!trim.isEmpty()) {
              found = trim;
              break;
          }
      }
      final String res = found;
    System.out.println(res);
  }
}
