class Test {

  interface A {
    int m();
  }

  interface B {
    int m(int i);
  }

  public static void main(String[] args) {
    A a = ()-> ((B)i -> i).m(3);
  }
}
