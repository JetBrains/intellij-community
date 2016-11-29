
class A2 {
  void method(MyObject obj) {
    obj.method1();
    if (<selection>obj.isCondition1()</selection>) {
      doOtherStaff();
    }
    obj.method2();
  }

  {
    method(new MyObject());
  }

  private void doOtherStaff() {

  }

  private class MyObject {
    public void method1() {

    }

    public boolean isCondition1() {
      return true;
    }

    public void method2() {

    }
  }
}