// "Move assignment to field declaration" "true"

class X {
  Object f = new Runnable() {
    void x(int p) {
      int f = p;
    }

    public void run() {

    }
  };
  X() {
      <caret>//
  }

  void x() {
    int i = f;
  }

  void x2() {
    f = 9;
  }
}