// "Replace 'switch' with 'if'" "true-preview"
abstract class Test {
  abstract Object getObject();

  void foo(Object o) {
      if (o instanceof String) {
          System.out.println("one");
      } else {
          System.out.println("default");
      }
  }
}