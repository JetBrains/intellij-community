// "Fix all 'Constant expression can be evaluated' problems in file" "false"
class Test {
  void test() {
    // Do not suggest to compute for performance reasons
    String foo = "The quick brown fox jumps " + 10<caret>0000 + "times" + "over the lazy dog"+
                 "The quick brown fox jumps " + 100000 + "times" + "over the lazy dog"+
                 "The quick brown fox jumps " + 100000 + "times" + "over the lazy dog"+
                 "The quick brown fox jumps " + 100000 + "times" + "over the lazy dog"+
                 "The quick brown fox jumps " + 100000 + "times" + "over the lazy dog";
  }
}