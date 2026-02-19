// "Replace 'switch' with 'if'" "true-preview"
abstract class Test {
  abstract Object getObject();

  void foo(Object o) {
    <caret>switch (o) {
      case String s -> System.out.println("one");
      case null, default -> System.out.println("default");
    }
  }
}