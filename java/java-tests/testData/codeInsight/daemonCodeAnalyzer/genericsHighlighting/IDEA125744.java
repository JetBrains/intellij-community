class A {}
interface B {}

class MyTest {
  public <M extends A & B> M getInvokedMember() {
    return null;
  }
}

class Bar {
  void f(MyTest myTest) {
    B member = myTest.getInvokedMember();
  }
}
