// "Initialize in constructor" "true"
class A {
  private final int var;

  private Main() {
      var = <caret><selection>0</selection>;
  }
}