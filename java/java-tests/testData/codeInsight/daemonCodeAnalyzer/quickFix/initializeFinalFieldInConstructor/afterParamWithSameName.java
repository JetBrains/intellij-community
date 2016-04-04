// "Initialize in constructor" "true"
class A {
  private final int var;

  private Main(int var) {
      this.var = 0<caret>;
  }
}