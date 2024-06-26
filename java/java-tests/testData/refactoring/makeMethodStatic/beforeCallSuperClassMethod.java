public class Foo {

  private int counter;

  public Foo(int initialCounter) {
    this.counter = initialCounter;
  }

  void <caret>toBeRefactored() {
    new Foo(counter + 10) {
      void toImplement() {
        toCall();
      }
    }.toImplement();
  }

  void toCall() {
    System.out.println("Counter: " + counter);
  }

  public static void main(String[] args) {
    Foo foo = new Foo(5);
    foo.toBeRefactored();
  }
}