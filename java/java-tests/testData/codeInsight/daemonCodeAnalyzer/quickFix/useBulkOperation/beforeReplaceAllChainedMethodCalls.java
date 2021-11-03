// "Replace iteration with bulk 'List.replaceAll' call" "true"
import java.util.*;

class Main {
  void modifyStrings(List<String> strings) {
    for (int i = 0; i < strings.size(); i++) {
      strings.set<caret>(i, strings.get(i).trim().toLowerCase());
    }
  }
}