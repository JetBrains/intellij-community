// "Replace with lambda" "false"
class Test {
  interface I {
    void m();
  }
  
  {
    I i = new I<caret>() {
      public strictfp void m() {
        //do smth
      }
    }
  }
}
