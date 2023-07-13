// "Replace 'switch' with 'if'" "false"
abstract class Test {
  abstract Object getObject();

  void foo() {
    <caret>switch (o) {
      case null, default -> System.out.println("hello");
    }
  }
}