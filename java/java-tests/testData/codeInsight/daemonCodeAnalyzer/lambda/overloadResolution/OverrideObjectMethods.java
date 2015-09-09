abstract class A {
  public abstract int hashCode();
  public abstract boolean equals(Object obj);
  public abstract void foo();
  
  {
    new A() {
      @Override
      public int hashCode() {
        return <error descr="Abstract method 'hashCode()' cannot be accessed directly">super.hashCode()</error>;
      }

      @Override
      public boolean equals(Object obj) {
        return <error descr="Abstract method 'equals(Object)' cannot be accessed directly">super.equals(obj)</error>;
      }

      @Override
      public void foo() {
        <error descr="Abstract method 'foo()' cannot be accessed directly">super.foo()</error>;
      }
    };
  }
}
