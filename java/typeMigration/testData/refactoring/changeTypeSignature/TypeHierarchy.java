abstract class A<E> {
  abstract E foo();
}

abstract class B<T> extends A<T> {
}

class C extends B<S<caret>tring> {
  String foo() {
    return null;
  }

  void bar() {
    if (foo() == null) {
      //do smth
    }
  }
}