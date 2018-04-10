// "Replace with Optional.ofNullable() chain" "false"

static class Test {
  final String foo;

  Test() {
    String x = "foo";
    System.out.println(x =<caret>= null ? (foo = "bar") : (foo = x.trim()));
  }
}