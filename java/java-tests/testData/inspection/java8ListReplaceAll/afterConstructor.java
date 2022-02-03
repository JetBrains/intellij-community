// "Replace the loop with 'List.replaceAll'" "true"
import java.util.List;

class Main {
  void modifyStrings(List<String> strings) {
      strings.replaceAll(String::new);
  }
}