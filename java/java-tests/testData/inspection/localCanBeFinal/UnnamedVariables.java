class Main {
  void test() {
    int _ = 10;
    int <warning descr="Variable 'y' can have 'final' modifier">y</warning> = 20;
    System.out.println(y);
  }
  
  void forLoop(int[] <warning descr="Parameter 'data' can have 'final' modifier">data</warning>) {
    for (int _ : data) {
      System.out.println("1");
    }
    for (int <warning descr="Variable 'n' can have 'final' modifier">n</warning> : data) {
      System.out.println("1");
    }
  }
  
  record R(int x, int y) {}
  
  void pattern(Object <warning descr="Parameter 'obj' can have 'final' modifier">obj</warning>) {
    if (obj instanceof R(int <warning descr="Parameter 'x' can have 'final' modifier">x</warning>, int _)) {
      System.out.println(x);
    }
    switch(obj) {
      case R(int <warning descr="Parameter 'x' can have 'final' modifier">x</warning>, int _) -> System.out.println(x);
      default -> {}
    }
    switch(obj) {
      case R(_, int <warning descr="Parameter 'y' can have 'final' modifier">y</warning>) -> System.out.println(y);
      default -> {}
    }
  }
  
  void catchParam() {
    try {
      System.out.println("Hello");
    }
    catch (RuntimeException <warning descr="Parameter 'ex' can have 'final' modifier">ex</warning>) {
      System.out.println("oops");
    }
    catch (Exception _) {
      System.out.println("oops");
    }
  }
  
  interface Fn {
    void test(int x, int y);
  }
  
  void lambda() {
    Fn <warning descr="Variable 'fn' can have 'final' modifier">fn</warning> = (int x, int _) -> System.out.println(x);
  }
}

