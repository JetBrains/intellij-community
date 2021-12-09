// "Replace the loop with 'List.replaceAll'" "true"
import java.util.*;

class Main {
  void modifyStrings(List<String> strings) {
    for (int i = 0; i < strings.size(); i++)<caret> {
      strings.set(i, modifyString(strings.get(i)));
    }
  }

  String modifyString(String str) {
    return str.repeat(2);
  }
}