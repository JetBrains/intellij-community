// "Replace with collect" "true"

import java.util.List;

public class Test {
  public void test(List<String> list) {
    StringBuilder sb = new StringBuilder();
    sb.append(list.get(0));
    for (int <caret>i=1; i<Math.min(10, list.size()); i++) {
      sb.append(",").append(list.get(i));
    }
    System.out.println(sb.toString());
  }
}