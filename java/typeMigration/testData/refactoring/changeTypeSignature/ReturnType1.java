class A<T> {
  T foo(){
    return null;
  }
}

public class B extends A<S<caret>tring> {
  String foo(){return null;}

  void bar() {
    foo();
    if (foo() == null) return;
    System.out.println(foo());
  }
}