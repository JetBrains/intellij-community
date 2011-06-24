public class Test {
   public void foo(Test t) {
     foo(((Test) new Test()));
   }
}