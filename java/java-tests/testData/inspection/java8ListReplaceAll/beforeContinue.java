// "Replace the loop with 'List.replaceAll'" "false"
import java.util.List;

class Main {
  void modifyStrings(List<String> strings) {
    for<caret> (int i = 0; i < strings.size(); i++) {
      if (Math.random() > 0.5) continue;
      strings.set(i, strings.get(i).toLowerCase());
    }
  }
}