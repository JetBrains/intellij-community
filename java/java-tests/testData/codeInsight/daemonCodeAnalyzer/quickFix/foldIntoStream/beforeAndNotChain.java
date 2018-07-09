// "Fold expression into Stream chain" "true"
class Test {
  boolean foo(String a, String b, String c, String d) {
    return !a.startsWith("xyz") &<caret>& !b.startsWith("xyz") && !c.startsWith("xyz") && !d.startsWith("xyz");
  }
}