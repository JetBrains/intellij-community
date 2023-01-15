import org.jetbrains.annotations.*;

class Test {
  void nullableWithUnconditionalPatternLabel(@Nullable Integer i) {
    switch (i) {
      case 1:
        break;
      case (Integer ii && true):
        break;
    }
  }

  void nullableSetNullWithUnconditionalPatternLabel(@Nullable Integer i) {
    i = null;
    switch (i) {
      case 1:
        break;
      case <warning descr="Switch label '((Integer ii && true))' is the only reachable in the whole switch">((Integer ii && true))</warning>:
        break;
    }
  }

  void nullableSetNotNullWithUnconditionalPatternLabel(@Nullable Integer i) {
    i = 1;
    switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning>:
        break;
      case Integer ii && true:
        break;
    }
  }

  void unknownWithUnconditionalPatternLabel(Integer i) {
    switch (i) {
      case 1:
        break;
      case Integer ii && true:
        break;
    }
  }

  void unknownSetNullWithUnconditionalPatternLabel(Integer i) {
    i = null;
    switch (i) {
      case 1:
        break;
      case <warning descr="Switch label '(Integer ii && true)' is the only reachable in the whole switch">(Integer ii && true)</warning>:
        break;
    }
  }

  void unknownSetNotNullWithUnconditionalPatternLabel(Integer i) {
    i = 1;
    switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning>:
        break;
      case (Integer ii):
        break;
    }
  }

  void notNullWithUnconditionalPatternLabel(@NotNull Integer i) {
    switch (i) {
      case 1:
        break;
      case Integer ii && true:
        break;
    }
  }

  void notNullSetNullWithUnconditionalPatternLabel(@NotNull Integer i) {
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

  void notNullSetNotNullWithUnconditionalPatternLabel(@NotNull Integer i) {
    i = 1;
    switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning>:
        break;
      case Object o:
        break;
    }
  }

  void nullableCallWithGuardedNotUnconditionalPatternLabel() {
    switch (<warning descr="Unboxing of 'createNullValue()' may produce 'NullPointerException'">createNullValue()</warning>) {
      case 1:
        break;
      case <warning descr="Switch label '((Object o && false))' is unreachable">((Object o && false))</warning>:
        break;
      case default:
        break;
    }
  }

  void nullableCallWithUnconditionalPatternLabel() {
    switch (createNullValue()) {
      case 1:
        break;
      case ((Object o)):
        break;
    }
  }

  void unknownCallWithUnconditionalPatternLabel() {
    switch (createValue()) {
      case 1:
        break;
      case Integer ii && true:
        break;
    }
  }

  void notNullCallWithUnconditionalPatternLabel() {
    switch (createNotNullValue()) {
      case 1, 2:
        break;
      case Object o && true:
        break;
    }
  }

  // expressions

  int nullableWithUnconditionalPatternLabelExpr(@Nullable Integer i) {
    return switch (i) {
      case 1 -> 1;
      case (Integer ii && true) -> 2;
    };
  }

  int nullableSetNullWithUnconditionalPatternLabelExpr(@Nullable Integer i) {
    i = null;
    return switch (i) {
      case 1 -> 1;
      case <warning descr="Switch label '((Integer ii && true))' is the only reachable in the whole switch">((Integer ii && true))</warning> -> 2;
    };
  }

  int nullableSetNotNullWithUnconditionalPatternLabelExpr(@Nullable Integer i) {
    i = 1;
    return switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning> -> 1;
      case Integer ii && true -> 2;
    };
  }

  int unknownWithUnconditionalPatternLabelExpr(Integer i) {
    return switch (i) {
      case 1 -> 1;
      case Integer ii && true -> 2;
    };
  }

  int unknownSetNullWithUnconditionalPatternLabelExpr(Integer i) {
    i = null;
    return switch (i) {
      case 1 -> 1;
      case <warning descr="Switch label '(Integer ii && true)' is the only reachable in the whole switch">(Integer ii && true)</warning> -> 2;
    };
  }

  int unknownSetNotNullWithUnconditionalPatternLabelExpr(Integer i) {
    i = 1;
    return switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning> -> 1;
      case (Integer ii) -> 2;
    };
  }

  int notNullWithUnconditionalPatternLabelExpr(@NotNull Integer i) {
    return switch (i) {
      case 1 -> 1;
      case Integer ii && true -> 2;
    };
  }

  int notNullSetNullWithUnconditionalPatternLabelExpr(@NotNull Integer i) {
    i = null;
    return switch (i) {
      case 1 -> 1;
      case Integer ii && false -> 2;
      case <warning descr="Switch label '(Integer ii && true)' is the only reachable in the whole switch">(Integer ii && true)</warning> -> 3;
    };
  }

  int notNullSetNotNullWithUnconditionalPatternLabelExpr(@NotNull Integer i) {
    i = 1;
    return switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning> -> 1;
      case Object o -> 2;
    };
  }

  int nullableCallWithGuardedNotUnconditionalPatternLabelExpr() {
    return switch (<warning descr="Unboxing of 'createNullValue()' may produce 'NullPointerException'">createNullValue()</warning>) {
      case 1 -> 1;
      case <warning descr="Switch label '((Object o && false))' is unreachable">((Object o && false))</warning> -> 2;
      case default -> 3;
    };
  }

  int nullableCallWithUnconditionalPatternLabelExpr() {
    return switch (createNullValue()) {
      case 1 -> 1;
      case ((Object o)) -> 2;
    };
  }

  int unknownCallWithUnconditionalPatternLabelExpr() {
    return switch (createValue()) {
      case 1 -> 1;
      case Integer ii && true -> 2;
    };
  }

  int notNullCallWithUnconditionalPatternLabelExpr() {
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