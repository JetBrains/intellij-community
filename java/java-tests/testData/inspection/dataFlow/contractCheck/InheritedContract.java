abstract class A {
  @Override
  public boolean equals(Object object) {
    if(object == null) return <warning descr="Contract clause 'null -> false' is violated">true</warning>;
    return false;
  }
}
abstract class B {
  @Override
  public boolean equals(Object object) {
    throw new AssertionError();
  }
}