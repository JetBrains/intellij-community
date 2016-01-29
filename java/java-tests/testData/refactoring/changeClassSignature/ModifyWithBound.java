public class Main {

  class <caret>B<X extends Long> {}

  public void someMethod() {
    B<Long> b = new B<>();
  }
}
