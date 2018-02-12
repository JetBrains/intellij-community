// "Replace with anyMatch()" "false"

import java.util.List;

public class Main {
  public void process(List<String> strings) {
    String item = "FOO";
    for(String str : st<caret>rings) {
      String trimmed = str.trim();
      if(trimmed.equals(item)) {
        item = "BAR";
        break;
      }
    }
    System.out.println(item);
  }
}
