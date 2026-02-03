class A {
  void bar(int i){
    method(i);
    method(i, "a");
    method(i, "a", "b");
  }

  void m<caret>(int i, String... args) {
    method(i, args);
  }

  void method(int i, String... args) {
  }
}