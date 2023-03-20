package my.main;

class Main {
  void foo() throws Exception {
    Class.forName("my.impl<caret>.Impl");
  }
}