// "Replace with collect" "true"

import java.util.List;

public class Test {
  static String test(List<String> list) {
    int BUFLENGTH = 42;
    StringBuffer sb = new StringBuffer(BUFLENGTH);
    for<caret> (int i = 0; i < BUFLENGTH >> 1; i++) {
      sb.append(true? "a" : "b");
    }
  }
}