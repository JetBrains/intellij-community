// "Create constructor" "true"
class Test {
    void foo() throws Ex1 {}
    void bar() throws Ex2 {}
    public void t() {
      try {
        foo();
        bar();
      }
      catch (Ex1 | Ex2 e) {
        new A(<caret>e);
      }
    }
}

class A {
}
class Ex1 extends Exception {}
class Ex2 extends Exception {}