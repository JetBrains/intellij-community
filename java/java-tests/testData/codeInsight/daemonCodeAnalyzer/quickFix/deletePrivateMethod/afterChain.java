// "Delete method 'test()'|->… along with other private methods used only there" "true"
class X {

    private void test2() {
  
    }
  
    public void test3() {
        test4();
    }
  
    private void test4() {
  
    }

}
