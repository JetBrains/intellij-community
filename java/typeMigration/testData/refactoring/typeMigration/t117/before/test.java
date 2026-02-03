import java.util.*;
class Test {
  Number str;

  void foo(List<String> p) {
    for (Number number : p) {
      number = str;
    }
  }
}