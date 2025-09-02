// "Fix all 'Unnecessary Unicode escape sequence' problems in file" "true"
class X {
\u0009void test() {
    String s = "\u<caret>0061\u0062\u0063\u0064";
    String t = """
      \u0009\u000A""";
  }
}