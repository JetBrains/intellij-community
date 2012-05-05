// "Initialize in constructor" "true"
class A {
  private final int var;

  private Main() {
      var = <caret><selection>0</selection>;
  }

  private Main(int var) {
      this.var = 0;
  }
}