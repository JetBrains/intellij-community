import typeUse.*;

interface I<T> {
  @Nullable T getT();
}

abstract class X<T extends @NotNull Object> implements I<T> {
  public abstract T <warning descr="Overriding methods are not annotated">getT</warning>();
}

class Y extends X<@NotNull String> {
  @Override
  public String <warning descr="Not annotated method overrides method annotated with @NotNull">getT</warning>() {
    return null;
  }
}
