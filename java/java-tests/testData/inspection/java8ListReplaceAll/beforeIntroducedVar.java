// "Replace the loop with 'List.replaceAll'" "true"
import java.util.*;

class Main {
  void modifyStrings(List<String> strings) {
    for (int i = 0; i < strings.size(); i++)<caret> {
      String str = strings.get(i);
      strings.set(i, modifyString(str));
    }
  }

  static String modifyString(String str) {
    return str.repeat(2);
  }
}