
class A {
  private String prefix = "> ";

  void f<caret>oo() {
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        System.out.println(prefix);
      }
    };
  }
}

class B extends A {}