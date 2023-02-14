// "Remove 'if' statement" "true-preview"
class Test {
  void patternVariableAccessedOutsideOfExpression(Object o) {
    if (false && <caret>o instanceof String s) {
      System.out.println(s);
    }
  }
}