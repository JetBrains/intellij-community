// "Unroll loop" "true"
class Test {
  void test(Object y) {
    fo<caret>r(Object x : new Object[] {"one", 1, 1.0, 1.0f}) {
      System.out.println(x);
      if(Math.random() > 0.5) break;
    }
  }
}