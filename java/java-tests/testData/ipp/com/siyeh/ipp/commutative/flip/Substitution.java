class A<T> {
  void foo(A<T> a){}

  void bar(B b, B b1) {
    b./*in method call*/f<caret>oo(b1/*comment in arg*/);//end line comment
  }
}

class B extends A<String> {}