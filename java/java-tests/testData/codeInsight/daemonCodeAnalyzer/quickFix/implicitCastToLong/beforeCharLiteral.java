// "Fix all 'Integer multiplication or shift implicitly cast to 'long'' problems in file" "true"
class X {
  void test(int x) {
    long y = x *<caret> 'a';
  }
}