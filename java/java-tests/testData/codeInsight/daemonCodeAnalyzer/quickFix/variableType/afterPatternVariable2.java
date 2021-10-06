// "Change variable 'i' type to 'int[]'" "true"
class Test {
  void foo(Object o) {
    switch (o) {
      case int[] i -> System.out.println("int");
    }
  }
}