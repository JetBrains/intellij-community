class A {
    A() {
    }

    static A createA() {
        return new A();
    }
}
class B extends A {
  public static void main(String[] args) {
    new A() {

    };
  }
}