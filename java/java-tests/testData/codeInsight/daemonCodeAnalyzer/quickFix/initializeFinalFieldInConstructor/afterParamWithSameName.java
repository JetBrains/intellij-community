// "Initialize in constructor" "true"
class A {
  private final int var;

  private Main(int var) {
      this.var = <caret><selection>0</selection>;
  }
}