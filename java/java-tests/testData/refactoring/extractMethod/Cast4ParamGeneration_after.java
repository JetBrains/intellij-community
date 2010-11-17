public class Test {
  void foo(Object o) {
    if (o instanceof A) {
        newMethod((A) o);
    }
  }

    private void newMethod(A o) {
        ((A)o).bar();
    }
}

class A {
  void bar(){}
}