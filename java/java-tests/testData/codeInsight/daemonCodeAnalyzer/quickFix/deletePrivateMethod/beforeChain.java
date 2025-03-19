// "Delete method 'test()'|->… along with other private methods used only there" "true"
class X {
    private void t<caret>est() {
        test1();
        test2();
        test3();
        test4();
    }
  
    private void test1() {
        test2();
        test5();
    }
  
    private void test2() {
  
    }
  
    public void test3() {
        test4();
    }
  
    private void test4() {
  
    }
  
    private void test5() {
  
    }
}
