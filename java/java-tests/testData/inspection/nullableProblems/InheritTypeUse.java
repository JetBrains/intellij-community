import typeUse.*;

interface I<T> {
  @Nullable T getT();
}

abstract class X<T extends @NotNull Object> implements I<T> {
  abstract T <warning descr="Overridden methods are not annotated">getT</warning>();
}

class Y extends X<@NotNull String> {
  @Override
  String <warning descr="Not annotated method overrides method annotated with @NotNull">getT</warning>() {
    return null;
  }
}
