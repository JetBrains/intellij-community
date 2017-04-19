package my.main;

class Main {
  void foo() throws Exception {
    Class.forName("my.api.Api");
    Class.forName(<warning descr="The module 'API' does not export the package 'my.impl' to the module 'MAIN'">"my.impl.Impl"</warning>);
  }
}