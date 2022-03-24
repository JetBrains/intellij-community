// "Replace the loop with 'List.replaceAll'" "true"
import java.util.List;

class Main {
  void modifyStrings(List<String> strings) {
    for<caret> (int i = 0; i < strings.size(); i++) {
      String str = strings.get(i).toLowerCase();
      strings.set(i, str);
    }
  }
}