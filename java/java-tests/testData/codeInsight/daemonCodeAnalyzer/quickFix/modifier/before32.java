// "Make 'ss.s' public" "false"
// do not suggest intention because of statics problem

class ss {
  private int s;
}
class d extends ss {
  static void f() {
    int t = <caret>s;
  }
}