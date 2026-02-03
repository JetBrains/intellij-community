class Test {
  void bar() {
    new B<String>().foo("");
  }

  class A<T> {
    void foo(T t){}
  }

  class B<E> extends A<E> {
    void fo<caret>o(E e){System.out.println(e);}
  }
}

