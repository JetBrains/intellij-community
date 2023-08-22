// "Simplify" "true-preview"
class Test {
  String test(String foo) {
    return (false/*always false?*/) ? foo<caret> : null;
  }
}