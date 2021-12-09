// "Replace the loop with 'List.replaceAll'" "false"
import java.util.*;

class Main {
  void modifyStrings(List<String> strings) {
    for (int i = 0; i < strings.size(); i++)<caret> {
      if (Math.random() > 0.5) return;
      strings.set(i, strings.get(i).toLowerCase());
    }
  }
}