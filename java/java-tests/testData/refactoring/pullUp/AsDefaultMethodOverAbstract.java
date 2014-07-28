class Test {
  interface Printer {
    void foo();

  }

  abstract class AbstractPrinter implements Printer {
    @Override
    public void f<caret>oo() {
    }
  }
}