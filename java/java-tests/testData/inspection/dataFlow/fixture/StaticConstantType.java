class X {
  void test1(X arg, boolean b) {
    if (arg instanceof Z && b) return;
    if (arg == Y.y) {
      
    }
  }
  
  void test() {
    X x = getX();
    if (x instanceof Z || x == Y.y) {}
    if (x == Y.y) {}
  }
  
  native X getX();
}
class Y extends X {
  public static final Y y = new Y();
}
class Z extends X {}