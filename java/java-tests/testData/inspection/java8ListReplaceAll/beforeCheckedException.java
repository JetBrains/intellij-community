// "Replace the loop with 'List.replaceAll'" "false"
import java.io.IOException;
import java.util.*;

class Main extends ArrayList<String> {
  void modifyStrings(List<String> strings) throws IOException {
    for (int i = 0; i < strings.size(); i++)<caret> {
      strings.set(i, modifyString(strings.get(i)));
    }
  }

  static String modifyString(String str) throws IOException {
    return str.repeat(2);
  }
}