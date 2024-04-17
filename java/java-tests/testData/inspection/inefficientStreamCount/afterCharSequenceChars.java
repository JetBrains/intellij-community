// "Replace with 'CharSequence.length()'" "true-preview"
class X {
  void test(CharSequence cs) {
    var x = (long) cs.length();
  }
}