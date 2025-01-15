class ChildA extends MyAbstractClass implements MyInterface {
  @Override
  public void buggyMethod() {
    System.out.println("ChildA");
  }
  public void caller() {
    buggyMethod();
  }
}
abstract class MyAbstractClass {
  public void buggyMethod() {
    System.out.println("buggyMethod from MyAbstractClass");
  }
}
interface MyInterface {
  void buggy<caret>Method();
}