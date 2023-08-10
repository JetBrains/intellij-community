public class Test {
  int x = 42;
  class Inner<caret> {
    void test(){
      System.out.println(x);
    }
  }
}
