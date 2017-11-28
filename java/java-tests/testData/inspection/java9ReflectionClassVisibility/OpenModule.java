package my.main;

class Main {
  void foo() throws Exception {
    Class.forName("my.api.Api");
    Class.forName("my.impl.Impl");
  }
}