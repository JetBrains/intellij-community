class Test extends Super{
  public void context() {
    super.method();
  }

  @Override
  public void method() {}
}

class Test1 extends Super {
   @Override
   public void method() {}
}

class U {
  Super t = new Test();
}