public class IgnoreSomething {
  public sealed interface Foo permits Foo.A, Foo.B {
    record A(int value) implements Foo {}
    record B(int value) implements Foo {}
  }

  public class Bar {
    public int toCode(Foo foo) {
      return switch (foo) {
        case Foo.A _ -> 0;
        case Foo.B b<caret> -> 1;
      };
    }
  }
}
