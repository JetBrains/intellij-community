// "Fix all 'Loop can be collapsed with Stream API' problems in file" "false"

import java.util.List;

public class Main {
  void test(List<String> list) {
    for(String l : li<caret>st) {
      TEST:
    }
  }
}