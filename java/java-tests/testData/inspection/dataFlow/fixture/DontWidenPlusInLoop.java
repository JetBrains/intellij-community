import java.util.*;

public class DontWidenPlusInLoop {
  public static int parse(String value, int i) {
    do {
      i = value.indexOf('{', i);
      if (i != -1 && i == value.indexOf("{{", i)) {
        i += 2;
      }
      else {
        break;
      }
    }
    while (<warning descr="Condition 'i != -1' is always 'true'">i != -1</warning>);
    return i;
  }

  void test(List<String> list) {
    List<String> result = null;
    int count = 0;
    for (String s : list) {
      count++;
      if (count == 1) {
        result = new ArrayList<>();
      }
      result.add(s); // No possible NPE!
    }
  }
}