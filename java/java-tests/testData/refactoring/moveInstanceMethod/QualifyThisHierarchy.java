public class D {
  protected void iAmProtected() {
  }
}

class DImpl extends D {
  void f<caret>oo(F f) {
    class MyRunnable {
      public void run() {
        iAmProtected();
      }
    }
  }
}

class F {

}