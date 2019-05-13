// "Replace with collect" "true"

import java.util.List;

public class Test {
  public void test(List<String> list) {
    StringBuilder sb = new StringBuilder();
    for (int <caret>i=0; i<Math.min(10, list.size()); i++) {
      if(i != 0) {
        sb.append(",").append(list.get(i));
      } else {
        sb.append(list.get(i));
      }
    }
    System.out.println(sb.toString());
  }
}