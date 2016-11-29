// "Make 'C2' static" "true"
class C {
  public abstract static class C2 {
    abstract void m();
  }
}

class Test {

  public static void main(String[] args) {
    new C.C2() {
      @Override
      void m() {

      }
    };
  }
}