class ManySuperCalls {
  public void foo() {
    System.out.println();
  }
}

class ManySuperCallsImpl extends ManySuperCalls {
  public void foo() {
    super.foo();
    super.foo();
  }
}