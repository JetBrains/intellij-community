// "Replace the loop with 'List.replaceAll'" "true"
import java.util.*;

class Main {
  void modifyStrings(List<String> strings) {
      strings.replaceAll(String::toLowerCase);
  }
}