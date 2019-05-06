/*
Value is always false (list == null)
  'list' was assigned (=)
    According to contract, method 'loadList' always returns 'new' value (loadList)
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