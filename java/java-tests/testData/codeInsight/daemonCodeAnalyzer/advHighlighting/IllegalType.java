
  class Foo {
    int k;
    Class c=<error descr="Unknown class: 'k'">k</error>.class;
    void f() {
      <error descr="Unknown class: 'java.io'">java.io</error> javaio;
    }
  }
