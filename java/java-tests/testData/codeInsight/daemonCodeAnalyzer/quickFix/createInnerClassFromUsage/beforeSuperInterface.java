// "Create inner class 'MyRunnable'" "true"
public class Test {
  void bar(Class<? extends Runnable> c) {
  }

  void foo() {
    bar(My<caret>Runnable.class);
  }
}