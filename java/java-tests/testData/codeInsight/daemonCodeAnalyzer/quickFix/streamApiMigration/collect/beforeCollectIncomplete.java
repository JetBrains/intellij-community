// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.List;

public class Main {
  void test(List<String> list) {
    List<String> result = new ArrayList<>();
    f<caret>or (String s : list) {
      result.add(;
    }
  }
}