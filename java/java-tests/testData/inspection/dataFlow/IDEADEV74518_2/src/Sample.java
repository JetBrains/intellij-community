public abstract class NoWarnings {
    public void f() {
      boolean A = false;
      boolean B = false;

      while (true) {
          boolean f = g();
          A = A || f;
          B = B || !f;
          if (A && B) {
              return;
          }
      }
    }
  
    public abstract boolean g();
}
