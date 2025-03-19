import java.util.ArrayList;
import org.jetbrains.annotations.*;

class Test {
  void nullableWithUnconditionalPatternLabel(@Nullable Integer i) {
    switch (<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>) {
      case 1:
        break;
      case Integer ii when <warning descr="Condition is always true">true</warning>:
        break;
    }
  }

  void nullableSetNullWithUnconditionalPatternLabel(@Nullable Integer i) {
    i = null;
    switch (<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>) {
      case 1:
        break;
      case Integer ii when <warning descr="Condition is always true">true</warning>:
        break;
    }
  }

  void nullableSetNotNullWithUnconditionalPatternLabel(@Nullable Integer i) {
    i = 1;
    switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning>:
        break;
      case Integer ii when <warning descr="Condition is always true">true</warning>:
        break;
    }
  }

  void unknownWithUnconditionalPatternLabel(Integer i) {
    switch (i) {
      case 1:
        break;
      case Integer ii when <warning descr="Condition is always true">true</warning>:
        break;
    }
  }

  void unknownSetNullWithUnconditionalPatternLabel(Integer i) {
    i = null;
    switch (<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>) {
      case 1:
        break;
      case Integer ii when <warning descr="Condition is always true">true</warning>:
        break;
    }
  }

  void unknownSetNotNullWithUnconditionalPatternLabel(Integer i) {
    i = 1;
    switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning>:
        break;
      case Integer ii:
        break;
    }
  }

  void notNullWithUnconditionalPatternLabel(@NotNull Integer i) {
    switch (i) {
      case 1:
        break;
      case Integer ii when <warning descr="Condition is always true">true</warning>:
        break;
    }
  }

  void notNullSetNullWithUnconditionalPatternLabel(@NotNull Integer i) {
    i = null;
    switch (<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>) {
      case 1:
        break;
      case Integer ii when Math.random() > 0.5:
        break;
      case Integer ii when <warning descr="Condition is always true">true</warning>:
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
      case Object o when <warning descr="Condition '!new ArrayList<String>().isEmpty()' is always 'false'">!new ArrayList<String>().isEmpty()</warning>:
        break;
      default:
        break;
    }
  }

  void nullableCallWithUnconditionalPatternLabel() {
    switch (<warning descr="Unboxing of 'createNullValue()' may produce 'NullPointerException'">createNullValue()</warning>) {
      case 1:
        break;
      case Object o:
        break;
    }
  }

  void unknownCallWithUnconditionalPatternLabel() {
    switch (createValue()) {
      case 1:
        break;
      case Integer ii when <warning descr="Condition is always true">true</warning>:
        break;
    }
  }

  void notNullCallWithUnconditionalPatternLabel() {
    switch (createNotNullValue()) {
      case 1, 2:
        break;
      case Object o when <warning descr="Condition is always true">true</warning>:
        break;
    }
  }

  // expressions

  int nullableWithUnconditionalPatternLabelExpr(@Nullable Integer i) {
    return switch (<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>) {
      case 1 -> 1;
      case Integer ii when <warning descr="Condition is always true">true</warning> -> 2;
    };
  }

  int nullableSetNullWithUnconditionalPatternLabelExpr(@Nullable Integer i) {
    i = null;
    return switch (<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>) {
      case 1 -> 1;
      case Integer ii when <warning descr="Condition is always true">true</warning> -> 2;
    };
  }

  int nullableSetNotNullWithUnconditionalPatternLabelExpr(@Nullable Integer i) {
    i = 1;
    return switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning> -> 1;
      case Integer ii when <warning descr="Condition is always true">true</warning> -> 2;
    };
  }

  int unknownWithUnconditionalPatternLabelExpr(Integer i) {
    return switch (i) {
      case 1 -> 1;
      case Integer ii when <warning descr="Condition is always true">true</warning> -> 2;
    };
  }

  int unknownSetNullWithUnconditionalPatternLabelExpr(Integer i) {
    i = null;
    return switch (<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>) {
      case 1 -> 1;
      case Integer ii when <warning descr="Condition is always true">true</warning> -> 2;
    };
  }

  int unknownSetNotNullWithUnconditionalPatternLabelExpr(Integer i) {
    i = 1;
    return switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning> -> 1;
      case Integer ii -> 2;
    };
  }

  int notNullWithUnconditionalPatternLabelExpr(@NotNull Integer i) {
    return switch (i) {
      case 1 -> 1;
      case Integer ii when <warning descr="Condition is always true">true</warning> -> 2;
    };
  }

  int notNullSetNullWithUnconditionalPatternLabelExpr(@NotNull Integer i) {
    i = null;
    return switch (<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>) {
      case 1 -> 1;
      case Integer ii when Math.random() > 0.5 -> 2;
      case Integer ii when <warning descr="Condition is always true">true</warning> -> 3;
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
      case Object o when Math.random() > 0.5 -> 2;
      default -> 3;
    };
  }

  int nullableCallWithUnconditionalPatternLabelExpr() {
    return switch (<warning descr="Unboxing of 'createNullValue()' may produce 'NullPointerException'">createNullValue()</warning>) {
      case 1 -> 1;
      case Object o -> 2;
    };
  }

  int unknownCallWithUnconditionalPatternLabelExpr() {
    return switch (createValue()) {
      case 1 -> 1;
      case Integer ii when <warning descr="Condition is always true">true</warning> -> 2;
    };
  }

  int notNullCallWithUnconditionalPatternLabelExpr() {
    return switch (createNotNullValue()) {
      case 1, 2 -> 1;
      case Object o when <warning descr="Condition is always true">true</warning> -> 2;
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