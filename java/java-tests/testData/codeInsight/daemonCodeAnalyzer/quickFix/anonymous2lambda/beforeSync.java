// "Replace with lambda" "false"
class Test {
  interface I {
    void m();
  }
  
  {
    I i = new I<caret>() {
      public synchronized void m() {
        //do smth
      }
    }
  }
}
