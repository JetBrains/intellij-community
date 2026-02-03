public class Test {
  public void foo() {
    Runnable r = new Runnable() {
      public void run(){
      }
      private void a(){}
    }
    class A {
      void a(){
        Runnable a = new Runnable() {
          public void run(){
          }
          private void aa(){}
        }
        class A1 {
          void a1(){
            Runnable a1 = new Runnable() {
              public void run(){
              }
              private void aa1(){}
            }
          }
        }
      }
    }
  }
  class B {
    void b() {
      Runnable b = new Runnable() {
        public void run(){
        }
        private void bb(){}
      }
    }
  }
}