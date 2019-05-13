// "Replace with collect" "true"

import java.util.List;

public class Test {
  private static String work2(List<String> strs) {
    StringBuilder sb = new StringBuilder();
    sb.append("{"); // before
    String separator = "";
    for <caret> (String str : strs) {
      sb.append(separator); // inside
      sb.append(str);
      separator = ",";
    }
    sb.append("}"); // after
    return sb.toString();
  }
}