// "Unroll loop" "true"
class Test {
  void test() {
    fo<caret>r(Object x : new Object[] {"one", 1, 1.0, 1.0f}) {
      if(Math.random() > 0.5) break;
      System.out.println(x);
    }
  }

  void foo(boolean b) {}
}