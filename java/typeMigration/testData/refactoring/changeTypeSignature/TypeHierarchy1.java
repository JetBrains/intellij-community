abstract class A<E> {
  public abstract E foo();
}

abstract class B<T> extends A<T> {
}

public class C extends B<S<caret>tring> {
  public String foo() {
    return null;
  }

  void bar() {
    if (foo() == null) {
      //do smth
    }
    System.out.println(foo());
  }
}