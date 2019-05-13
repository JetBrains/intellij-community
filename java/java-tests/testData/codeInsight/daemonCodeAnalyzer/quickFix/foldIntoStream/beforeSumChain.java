// "Fold expression into Stream chain" "true"
class Test {
  int foo(String a, String b, String c, String d) {
    return a.length()+b.length()+c.length()+<caret>d.length();
  }
}