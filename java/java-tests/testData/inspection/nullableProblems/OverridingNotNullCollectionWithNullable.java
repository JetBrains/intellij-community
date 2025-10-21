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
  @Nullable <warning descr="Overriding a collection of non-null elements with a collection of nullable elements">String @NotNull []</warning> getStrings() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull <warning descr="Overriding a collection of non-null elements with a collection of nullable elements">List<@Nullable String></warning> getStringList() {
    throw new UnsupportedOperationException();
  }

  void foo(@NotNull <warning descr="Overriding a collection of nullable elements with a collection of non-null elements">String @NotNull []</warning> p1,
           @NotNull <warning descr="Overriding a collection of nullable elements with a collection of non-null elements">List<@NotNull String></warning> p2) {

  }
}