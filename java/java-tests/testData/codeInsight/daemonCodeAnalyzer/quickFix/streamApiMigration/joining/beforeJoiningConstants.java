// "Replace with collect" "true"

import java.util.List;

public class Test {
  static String test(List<String> list) {
    int BUFLENGTH = 42;
    char CH = 'a';

    StringBuffer sb = new StringBuffer(BUFLENGTH);
    for<caret> (int i = 0; i < BUFLENGTH >> 1; i++) {
      sb.append('\u041b');
      sb.append(CH);
      sb.append('i');
    }
  }
}