public sealed class Superclass permits B, Subclass {
  public void foo() {}
}

final class B extends Superclass {}