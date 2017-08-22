// "Replace with collect" "false"

import java.util.List;

public class Test {
  private static String work2(List<String> strs) {
    StringBuilder sb = new StringBuilder();
    String separator = "!!!!";
    sb.append("{");
    for <caret> (String str : strs) {
      sb.append(separator);
      sb.append(str);
      separator = ",";
    }
    sb.append("}");
    return sb.toString();
  }
}