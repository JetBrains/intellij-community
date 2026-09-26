class NonTrivialSuperMethod {

  private int a() {
    return 1;
  }

  public int b() {
    //             int a = a();
    //             return a;
    return a();
  }

  private class B extends NonTrivialSuperMethod {
    @Override
    public int <warning descr="Method 'b()' does not call 'super.b()'">b</warning>() {
      return 231;
    }
  }
}