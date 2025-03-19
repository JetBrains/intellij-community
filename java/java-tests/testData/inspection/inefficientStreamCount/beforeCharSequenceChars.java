// "Replace with 'CharSequence.length()'" "true-preview"
class X {
  void test(CharSequence cs) {
    var x = cs.chars().<caret>count();
  }
}