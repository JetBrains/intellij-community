class Test <T> {
   void <caret>foo(T t) {
   }
}

class I extends Test<String>{}

class Usage {
  void bar() {
    new Test<Integer>().foo(1);
  }
}