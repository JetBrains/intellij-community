// "Replace iteration with bulk 'List.replaceAll' call" "true"
import java.util.*;

class Main {
  void modifyStrings(List<String> strings) {
    for (int i = 0; i < strings.size(); i++) {
      String str = strings.get(i);
      strings.set<caret>(i, modifyString(str));
    }
  }

  static String modifyString(String str) {
    return str.repeat(2);
  }
}