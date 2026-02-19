// "Fix all 'Loop can be replaced with 'List.replaceAll()'' problems in file" "true"
import java.util.List;

class Main {
  void modifyStrings1(List<String> strings) {
      strings.replaceAll(this::modifyString);
  }

  void modifyStrings2(List<String> strings) {
      strings.replaceAll(String::trim);
  }

  String modifyString(String str) {
    return str.repeat(2);
  }
}