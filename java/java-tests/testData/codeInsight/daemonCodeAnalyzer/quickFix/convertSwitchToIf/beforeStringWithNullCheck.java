// "Replace 'switch' with 'if'" "true-preview"
abstract class Test {
  abstract Object getObject();

  void foo(String s) {
    <caret>switch (s) {
      case null, "zero" -> System.out.println(0);
      case "one" -> System.out.println(1);
      default -> {}
    }
  }
}