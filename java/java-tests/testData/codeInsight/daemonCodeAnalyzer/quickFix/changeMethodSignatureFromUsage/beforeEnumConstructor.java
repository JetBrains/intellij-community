// "Add 'int' as 3rd parameter to constructor 'E'" "true-preview"
enum E {
  FIRST("a", "b", <caret>1);

  private final String a;
  private final String b;

  E(String a, String b) {
    this.a = a;
    this.b = b;
  }
}
