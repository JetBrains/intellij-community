/*
Value is always false (list == null; line#18)
  'list' was assigned (=; line#17)
    According to contract, method 'loadList' always returns 'new' value (loadList; line#17)
 */

import java.util.List;
import org.jetbrains.annotations.Contract;

class Test {
  @Contract("-> new")
  List<String> loadList() {
    return new ArrayList<>();
  }

  void test() {
    List<String> list = loadList();
    if(<selection>list == null</selection>) {}
  }
}