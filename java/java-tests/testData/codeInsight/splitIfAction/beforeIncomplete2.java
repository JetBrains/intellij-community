public class SplitCondition {
  void test(int x) {
    if(x > 0 &<caret>& x < 10) {}
    else if(x > 0 && x > 50)
  }
}
