// "Move assignment to field declaration" "true"

class X {
  Object f;
  X() {
    <caret>f = new Runnable() {
      void x(int p) {
        int f = p;
      }

      public void run() {

      }
    };
    //
  }

  void x() {
    int i = f;
  }

  void x2() {
    f = 9;
  }
}