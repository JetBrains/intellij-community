// "Replace iteration with bulk 'List.replaceAll' call" "true"
import java.util.*;

class Main {
  void modifyStrings(List<String> strings) {
      strings.replaceAll(s -> s.trim().toLowerCase());
  }
}