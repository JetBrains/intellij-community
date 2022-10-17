// "Replace with 'yield'" "false"
class X {
  int test(String s) {
    return switch (s) {
      case "foo" -> {
        System.out.println("hello");
        return<caret>;
      }
      case "bar" -> 2;
    };
  }
}