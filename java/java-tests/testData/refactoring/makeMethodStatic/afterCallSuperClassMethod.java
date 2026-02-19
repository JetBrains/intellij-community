public class Foo {

  private int counter;

  public Foo(int initialCounter) {
    this.counter = initialCounter;
  }

  static void toBeRefactored(final Foo anObject) {
    new Foo(anObject.counter + 10) {
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
    Foo.toBeRefactored(foo);
  }
}