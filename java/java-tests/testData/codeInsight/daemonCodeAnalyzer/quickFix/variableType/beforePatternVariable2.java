// "Change variable 'i' type to 'int[]'" "true"
class Test {
  void foo(Object o) {
    switch (o) {
      case int i<caret> -> System.out.println("int");
    }
  }
}