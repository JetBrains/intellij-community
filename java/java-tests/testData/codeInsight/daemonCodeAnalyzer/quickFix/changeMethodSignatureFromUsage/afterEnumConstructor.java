// "Add 'int' as 3rd parameter to method 'E'" "true"
enum E {
  FIRST("a", "b", 1);

  private final String a;
  private final String b;

  E(String a, String b, int i) {
    this.a = a;
    this.b = b;
  }
}
