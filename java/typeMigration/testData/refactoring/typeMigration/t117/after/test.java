import java.util.*;
class Test {
  String str;

  void foo(List<String> p) {
    for (String number : p) {
      number = str;
    }
  }
}