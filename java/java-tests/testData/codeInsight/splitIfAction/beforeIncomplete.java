public class SplitCondition {
  void test(boolean foo) {
    if(foo && foo &<caret>& ) {}
  }
}
