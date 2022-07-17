// "Fix all 'Loop can be replaced with 'List.replaceAll()'' problems in file" "true"
import java.util.List;

class Main {
  void modifyStrings1(List<String> strings) {
    for<caret> (int i = 0; i < strings.size(); i++) {
      strings.set(i, modifyString(strings.get(i)));
    }
  }

  void modifyStrings2(List<String> strings) {
    for (int i = 0; i < strings.size(); i++) {
      strings.set(i, strings.get(i).trim());
    }
  }

  String modifyString(String str) {
    return str.repeat(2);
  }
}