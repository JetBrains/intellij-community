interface MyInterface {
  int myMethod();
}
class MyTemplatedClass<T extends MyInterface> {
  protected T myInterfaceField;


    public int myMethod() {
        return myInterfaceField.myMethod();
    }
}
