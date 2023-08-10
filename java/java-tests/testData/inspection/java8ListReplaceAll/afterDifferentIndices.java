// "Replace the loop with 'List.replaceAll'" "true"
import java.util.List;

class Main {
  void modifyStrings(List<String> strings) {
    int j = 0;
      strings.replaceAll(ignored -> strings.get(j));
  }
}