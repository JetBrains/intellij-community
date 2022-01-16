// "Change variable 'i' type to 'Integer'" "true"
class Test {
  void foo(Object o) {
    switch (o) {
      case int i<caret> -> System.out.println("int");
    }
  }
}