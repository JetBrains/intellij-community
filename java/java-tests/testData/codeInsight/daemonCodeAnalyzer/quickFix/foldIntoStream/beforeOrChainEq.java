// "Fold expression into Stream chain" "false"
class Test {
  boolean foo(String a, String b, String c, String d) {
    return a == null || b == null || c == null || d == <caret>null;
  }
}