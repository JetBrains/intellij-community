// "Replace the loop with 'List.replaceAll'" "true"
import java.util.ArrayList;
import java.util.List;

class Main extends ArrayList<String> {
  void modifyStrings(List<String> strings) {
      this.replaceAll(Main::modifyString);
  }

  static String modifyString(String str) {
    return str.repeat(2);
  }
}