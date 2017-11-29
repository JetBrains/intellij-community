class Test {
  interface Printer {
      default void foo() {
      }

  }

  abstract class AbstractPrinter implements Printer {
  }
}