package my.main;

class Main {
  void foo() throws Exception {
    Class.forName(<warning descr="The module 'MAIN' does not have the module 'API' in requirements">"my.api.Api"</warning>);
    Class.forName(<warning descr="The module 'MAIN' does not have the module 'API' in requirements">"my.impl.Impl"</warning>);
  }
}