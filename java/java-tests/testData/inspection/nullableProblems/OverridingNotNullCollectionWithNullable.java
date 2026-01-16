import typeUse.*;
import java.util.*;

abstract class Parent {

  abstract @NotNull String @NotNull [] getStrings();

  abstract @NotNull List<@NotNull String> getStringList();

  abstract void foo(@Nullable String @NotNull [] p1,
                    @NotNull List<@Nullable String> p2);
}

class Child extends Parent {

  @Override
  @Nullable <warning descr="Overriding a class with nullable type arguments when a class with not-null type arguments is expected">String @NotNull []</warning> getStrings() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull <warning descr="Overriding a class with nullable type arguments when a class with not-null type arguments is expected">List<@Nullable String></warning> getStringList() {
    throw new UnsupportedOperationException();
  }

  void foo(@NotNull <warning descr="Overriding a class with nullable type arguments when a class with not-null type arguments is expected">String @NotNull []</warning> p1,
           @NotNull <warning descr="Overriding a class with nullable type arguments when a class with not-null type arguments is expected">List<@NotNull String></warning> p2) {

  }
}