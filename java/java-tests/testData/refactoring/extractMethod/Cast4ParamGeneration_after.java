public class Test {
  void foo(Object o) {
    if (o instanceof A) {
        newMethod((Object) o);
    }
  }

    private void newMethod(A o) {
        ((A)o).bar();
    }
}

class A {
  void bar(){}
}