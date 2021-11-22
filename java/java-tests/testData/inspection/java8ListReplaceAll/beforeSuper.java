// "Replace the loop with 'List.replaceAll'" "true"
import java.util.ArrayList;
import java.util.List;

class Main extends ArrayList<String> {
  void modifyStrings(List<String> strings) {
    for (int i = 0; i < super.size(); i++)<caret> {
      super.set(i, modifyString(super.get(i)));
    }
  }

  static String modifyString(String str) {
    return str.repeat(2);
  }
}