package p;

class BaseClass {
  public void method() {}
}

class SubClass extends BaseClass {
  public void method() {
  }
}

class Show1 {
  SubClass sub = new SubClass();
  {
    sub.method();
  }
}
class Show2 {
  BaseClass sub = new SubClass();
  {
    sub.method();
  }
}
