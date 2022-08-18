

class A {
  public int af() { return 1; }
  public static void main(String[] args) {
    int a = 0;
    a += <caret>af();
  }
}