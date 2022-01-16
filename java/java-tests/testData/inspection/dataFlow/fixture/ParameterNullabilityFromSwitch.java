import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

class Test {
  static void nullable(String s) {
    switch (s) {
      case "xyz" -> System.out.println("xyz");
      case null, default -> System.out.println("else");
    }
  }

  static void notNullable(String s) {
    switch (s) {
      case "xyz" -> System.out.println("xyz");
      case default -> System.out.println("else");
    }
  }

  public static void main(String[] args) {
    nullable(<warning descr="Passing 'null' argument to non-annotated parameter">null</warning>);
    notNullable(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
  }

  void nullableWithNullLabel(@Nullable E e) {
    switch (e) {
      case A:
        break;
      case B:
        break;
      case null:
        break;
    }
  }

  void nullableWithoutNullLabel(@Nullable E e) {
    switch (<warning descr="Dereference of 'e' may produce 'NullPointerException'">e</warning>) {
      case A:
        break;
      case B:
        break;
    }
  }

  void nullableSetNullWithNullLabel(@Nullable E e) {
    e = null;
    switch (e) {
      case A:
        break;
      case B:
        break;
      case <warning descr="Switch label 'null' is the only reachable in the whole switch">null</warning>:
        break;
    }
  }

  void nullableSetNotNullWithNullLabel(@Nullable E e) {
    e = E.A;
    switch (e) {
      case <warning descr="Switch label 'A' is the only reachable in the whole switch">A</warning>:
        break;
      case B:
        break;
      case null:
        break;
    }
  }

  void nullableSetNullWithoutNullLabel(@Nullable E e) {
    e = null;
    switch (<warning descr="Dereference of 'e' will produce 'NullPointerException'">e</warning>) {
      case A:
        break;
      case B:
        break;
    }
  }

  void nullableSetNotNullWithoutNullLabel(@Nullable E e) {
    e = E.A;
    switch (e) {
      case <warning descr="Switch label 'A' is the only reachable in the whole switch">A</warning>:
        break;
      case B:
        break;
    }
  }

  void unknownWithNullLabel(E e) {
    switch (e) {
      case A:
        break;
      case B:
        break;
      case null:
        break;
    }
  }

  void unknownWithoutNullLabel(E e) {
    switch (e) {
      case A:
        break;
      case B:
        break;
    }
  }

  void unknownSetNullWithNullLabel(E e) {
    e = null;
    switch (e) {
      case A:
        break;
      case B:
        break;
      case <warning descr="Switch label 'null' is the only reachable in the whole switch">null</warning>:
        break;
    }
  }

  void unknownSetNotNullWithNullLabel(E e) {
    e = E.B;
    switch (e) {
      case A:
        break;
      case <warning descr="Switch label 'B' is the only reachable in the whole switch">B</warning>:
        break;
      case null:
        break;
    }
  }

  void unknownSetNullWithoutNullLabel(E e) {
    e = null;
    switch (<warning descr="Dereference of 'e' will produce 'NullPointerException'">e</warning>) {
      case A:
        break;
      case B:
        break;
    }
  }

  void unknownSetNotNullWithoutNullLabel(E e) {
    e = E.B;
    switch (e) {
      case A:
        break;
      case <warning descr="Switch label 'B' is the only reachable in the whole switch">B</warning>:
        break;
    }
  }

  void notNullWithNullLabel(@NotNull E e) {
    switch (e) {
      case A, B:
        break;
      case <warning descr="Switch label 'null' is unreachable">null</warning>:
        break;
    }
  }

  void notNullWithoutNullLabel(@NotNull E e) {
    switch (e) {
      case A, B:
        break;
    }
  }

  void notNullSetNullWithNullLabel(@NotNull E e) {
    e = null;
    switch (e) {
      case A:
        break;
      case <warning descr="Switch label 'null' is the only reachable in the whole switch">null</warning>:
        break;
      case B:
        break;
    }
  }

  void notNullSetNotNullWithNullLabel(@NotNull E e) {
    e = E.B;
    switch (e) {
      case A:
        break;
      case <warning descr="Switch label 'B' is the only reachable in the whole switch">B</warning>:
        break;
      case null:
        break;
    }
  }

  void notNullSetNullWithoutNullLabel(@NotNull E e) {
    e = null;
    switch (<warning descr="Dereference of 'e' will produce 'NullPointerException'">e</warning>) {
      case A:
        break;
      case B:
        break;
    }
  }

  void notNullSetNotNullWithoutNullLabel(@NotNull E e) {
    e = E.B;
    switch (e) {
      case A:
        break;
      case <warning descr="Switch label 'B' is the only reachable in the whole switch">B</warning>:
        break;
    }
  }

  enum E {
    A, B
  }
}