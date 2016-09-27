import java.util.function.BooleanSupplier;

class A2 {
  void method(MyObject obj, BooleanSupplier anObject) {
      obj.method1();
      if (anObject.getAsBoolean()) {
          doOtherStaff();
      }
      obj.method2();
  }

  {
      final MyObject obj = new MyObject();
      method(obj, new BooleanSupplier() {
          public boolean getAsBoolean() {
              return obj.isCondition1();
          }
      });
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