// "Create type parameter 'B'" "false"

class A {
  public static void main(String[] args) {
    new A().new <caret>B();
  }
}