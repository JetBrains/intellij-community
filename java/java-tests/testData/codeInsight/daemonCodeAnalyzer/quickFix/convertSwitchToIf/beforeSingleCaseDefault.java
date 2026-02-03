// "Replace 'switch' with 'if'" "false"
abstract class Test {
  abstract Object getObject();

  void foo() {
    <caret>switch (o) {
      default -> System.out.println("hello");
    }
  }
}