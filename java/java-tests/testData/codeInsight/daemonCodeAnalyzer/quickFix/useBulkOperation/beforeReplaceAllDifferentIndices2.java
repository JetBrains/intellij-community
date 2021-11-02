// "Replace iteration with bulk 'List.replaceAll' call" "false"
import java.util.*;

class Main {
  void modifyStrings(List<String> strings) {
    int j = 0;
    for (int i = 0; i < strings.size(); i++) {
      strings<caret>.set(j, strings.get(j));
    }
  }
}