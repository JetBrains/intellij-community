// "Fix all 'Loop can be replaced with 'List.replaceAll()'' problems in file" "true"
import java.util.List;

class Main {
  void modifyStrings1(List<String> strings) {
    for<caret> (int i = 0; i < strings.size(); i++) {
      String replacement = strings.get(i).toUpperCase();
      strings.set(i, replacement);
    }
  }

  void modifyStrings2(List<String> strings) {
    for (int i = 0; i < strings.size(); i++) {
      String string = strings.get(i);
      String replacement = string.toUpperCase();
      strings.set(i, replacement);
    }
  }
}