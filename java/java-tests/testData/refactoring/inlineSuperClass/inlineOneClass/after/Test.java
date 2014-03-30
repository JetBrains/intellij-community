class Test {
  public void context() {
    method();
  }

  public void method() {}
}

class Test1 extends Super {
   @Override
   public void method() {}
}

class U {
  Super t = new Test1();
}