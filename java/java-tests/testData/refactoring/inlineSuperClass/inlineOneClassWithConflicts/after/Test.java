class Test {
  public void context() {
    method();
  }

  public void method() {}
}

class Test1 {
   public void method() {}
}

class U {
  Test t = new Test1();
}