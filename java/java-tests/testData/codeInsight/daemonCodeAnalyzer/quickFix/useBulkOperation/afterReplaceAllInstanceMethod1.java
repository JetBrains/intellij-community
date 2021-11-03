// "Replace iteration with bulk 'List.replaceAll' call" "true"
import java.util.*;

class Main {
  void modifyStrings(List<String> strings) {
      strings.replaceAll(this::modifyString);
  }

  String modifyString(String str) {
    return str.repeat(2);
  }
}