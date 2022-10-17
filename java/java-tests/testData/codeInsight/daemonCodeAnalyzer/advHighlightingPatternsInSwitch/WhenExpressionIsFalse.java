public class Main {
  void foo(Object obj) {
    final boolean flag = false;
    switch (obj) {
      case String s when <error descr="This case label has a guard that is a constant expression with value 'false'">false</error> -> {}
      case String s when s.length() < 0 -> {}
      case String s when <error descr="This case label has a guard that is a constant expression with value 'false'">((flag || flag)) && 1 < 0</error> -> {}
      default -> {}
    }
  }
}