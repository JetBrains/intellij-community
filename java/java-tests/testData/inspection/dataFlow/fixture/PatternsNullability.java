import org.jetbrains.annotations.*;

class Test {
  void nullableWithTotalPatternLabel(@Nullable Integer i) {
    switch (i) {
      case 1:
        break;
      case (Integer ii && true):
        break;
    }
  }

  void nullableSetNullWithTotalPatternLabel(@Nullable Integer i) {
    i = null;
    switch (i) {
      case 1:
        break;
      case <warning descr="Switch label '((Integer ii && true))' is the only reachable in the whole switch">((Integer ii && true))</warning>:
        break;
    }
  }

  void nullableSetNotNullWithTotalPatternLabel(@Nullable Integer i) {
    i = 1;
    switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning>:
        break;
      case Integer ii && true:
        break;
    }
  }

  void unknownWithTotalPatternLabel(Integer i) {
    switch (i) {
      case 1:
        break;
      case Integer ii && true:
        break;
    }
  }

  void unknownSetNullWithTotalPatternLabel(Integer i) {
    i = null;
    switch (i) {
      case 1:
        break;
      case <warning descr="Switch label '(Integer ii && true)' is the only reachable in the whole switch">(Integer ii && true)</warning>:
        break;
    }
  }

  void unknownSetNotNullWithTotalPatternLabel(Integer i) {
    i = 1;
    switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning>:
        break;
      case (Integer ii):
        break;
    }
  }

  void notNullWithTotalPatternLabel(@NotNull Integer i) {
    switch (i) {
      case 1:
        break;
      case Integer ii && true:
        break;
    }
  }

  void notNullSetNullWithTotalPatternLabel(@NotNull Integer i) {
    i = null;
    switch (i) {
      case 1:
        break;
      case Integer ii && false:
        break;
      case <warning descr="Switch label '(Integer ii && true)' is the only reachable in the whole switch">(Integer ii && true)</warning>:
        break;
    }
  }

  void notNullSetNotNullWithTotalPatternLabel(@NotNull Integer i) {
    i = 1;
    switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning>:
        break;
      case Object o:
        break;
    }
  }

  void nullableCallWithGuardedNotTotalPatternLabel() {
    switch (<warning descr="Unboxing of 'createNullValue()' may produce 'NullPointerException'">createNullValue()</warning>) {
      case 1:
        break;
      case <warning descr="Switch label '((Object o && false))' is unreachable">((Object o && false))</warning>:
        break;
      case default:
        break;
    }
  }

  void nullableCallWithTotalPatternLabel() {
    switch (createNullValue()) {
      case 1:
        break;
      case ((Object o)):
        break;
    }
  }

  void unknownCallWithTotalPatternLabel() {
    switch (createValue()) {
      case 1:
        break;
      case Integer ii && true:
        break;
    }
  }

  void notNullCallWithTotalPatternLabel() {
    switch (createNotNullValue()) {
      case 1, 2:
        break;
      case Object o && true:
        break;
    }
  }

  // expressions

  int nullableWithTotalPatternLabelExpr(@Nullable Integer i) {
    return switch (i) {
      case 1 -> 1;
      case (Integer ii && true) -> 2;
    };
  }

  int nullableSetNullWithTotalPatternLabelExpr(@Nullable Integer i) {
    i = null;
    return switch (i) {
      case 1 -> 1;
      case <warning descr="Switch label '((Integer ii && true))' is the only reachable in the whole switch">((Integer ii && true))</warning> -> 2;
    };
  }

  int nullableSetNotNullWithTotalPatternLabelExpr(@Nullable Integer i) {
    i = 1;
    return switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning> -> 1;
      case Integer ii && true -> 2;
    };
  }

  int unknownWithTotalPatternLabelExpr(Integer i) {
    return switch (i) {
      case 1 -> 1;
      case Integer ii && true -> 2;
    };
  }

  int unknownSetNullWithTotalPatternLabelExpr(Integer i) {
    i = null;
    return switch (i) {
      case 1 -> 1;
      case <warning descr="Switch label '(Integer ii && true)' is the only reachable in the whole switch">(Integer ii && true)</warning> -> 2;
    };
  }

  int unknownSetNotNullWithTotalPatternLabelExpr(Integer i) {
    i = 1;
    return switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning> -> 1;
      case (Integer ii) -> 2;
    };
  }

  int notNullWithTotalPatternLabelExpr(@NotNull Integer i) {
    return switch (i) {
      case 1 -> 1;
      case Integer ii && true -> 2;
    };
  }

  int notNullSetNullWithTotalPatternLabelExpr(@NotNull Integer i) {
    i = null;
    return switch (i) {
      case 1 -> 1;
      case Integer ii && false -> 2;
      case <warning descr="Switch label '(Integer ii && true)' is the only reachable in the whole switch">(Integer ii && true)</warning> -> 3;
    };
  }

  int notNullSetNotNullWithTotalPatternLabelExpr(@NotNull Integer i) {
    i = 1;
    return switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning> -> 1;
      case Object o -> 2;
    };
  }

  int nullableCallWithGuardedNotTotalPatternLabelExpr() {
    return switch (<warning descr="Unboxing of 'createNullValue()' may produce 'NullPointerException'">createNullValue()</warning>) {
      case 1 -> 1;
      case <warning descr="Switch label '((Object o && false))' is unreachable">((Object o && false))</warning> -> 2;
      case default -> 3;
    };
  }

  int nullableCallWithTotalPatternLabelExpr() {
    return switch (createNullValue()) {
      case 1 -> 1;
      case ((Object o)) -> 2;
    };
  }

  int unknownCallWithTotalPatternLabelExpr() {
    return switch (createValue()) {
      case 1 -> 1;
      case Integer ii && true -> 2;
    };
  }

  int notNullCallWithTotalPatternLabelExpr() {
    return switch (createNotNullValue()) {
      case 1, 2 -> 1;
      case Object o && true -> 2;
    };
  }

  @Nullable
  Integer createNullValue() {
    return null;
  }

  Integer createValue() {
    return 1;
  }

  @NotNull
  Integer createNotNullValue() {
    return 1;
  }
}