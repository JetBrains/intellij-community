// "Replace with 'yield'" "true-preview"
class X {
  int test(String s) {
    return switch (s) {
      case "foo" -> {
        System.out.println("hello");
        return <caret>123;
      }
      case "bar" -> 2;
    };
  }
}