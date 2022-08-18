// "Fold expression into Stream chain" "true-preview"
class Test {
  String foo(String a, String b, String c, String d) {
    return a.trim()+b.trim()+c.trim()+<caret>d.trim();
  }
}