public class Boo {
  @SuppressWarnings("UnusedDeclaration")
  public void foo() {

  }
}

class BooI {
  Boo boo;

    public void foo() {
        boo.foo();
    }
}
