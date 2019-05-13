class Parent {
  Parent() {
    callProtectedMethod();
  }
  protected void callProtectedMethod() { }
}

class Child extends Parent {
  private final Object myField;
  Child() {
    super();
    myField = new Object();
  }
  @Override
  protected void callProtectedMethod() {
    if (myField != null) {  // HERE myField CAN be null
      System.out.println();
    }
  }
}