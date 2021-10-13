import org.jetbrains.annotations.*;

class Test {
  void nullableWithNullLabel(@Nullable Integer i) {
    switch (i) {
      case 1:
        break;
      case null:
        break;
      case default:
        break;
    }
  }

  void nullableWithoutNullLabel(@Nullable Integer i) {
    switch (<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>) {
      case 1:
        break;
      case default:
        break;
    }
  }

  void nullableSetNullWithNullLabel1(@Nullable Integer i) {
    i = null;
    switch (i) {
      case 1:
        break;
      case <warning descr="Switch label 'null' is the only reachable in the whole switch">null</warning>:
        break;
      case default:
        break;
    }
  }

  void nullableSetNullWithNullLabel2(@Nullable Integer i) {
    i = null;
    switch (i) {
      case <warning descr="Switch label 'null' is the only reachable in the whole switch">null</warning>:
        break;
      case 1:
        break;
      case default:
        break;
    }
  }

  void nullableSetNotNullWithNullLabel(@Nullable Integer i) {
    i = 1;
    switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning>:
        break;
      case null:
        break;
      case default:
        break;
    }
  }

  void nullableSetNullWithoutNullLabel(@Nullable Integer i) {
    i = null;
    switch (<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>) {
      case 1:
        break;
      case default:
        break;
    }
  }

  void nullableSetNotNullWithoutNullLabel(@Nullable Integer i) {
    i = 1;
    switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning>:
        break;
      case default:
        break;
    }
  }

  void unknownWithNullLabel(Integer i) {
    switch (i) {
      case 1:
        break;
      case null:
        break;
      case default:
        break;
    }
  }

  void unknownWithoutNullLabel(Integer i) {
    switch (i) {
      case 1:
        break;
      case default:
        break;
    }
  }

  void unknownSetNullWithNullLabel(Integer i) {
    i = null;
    switch (i) {
      case 1:
        break;
      case <warning descr="Switch label 'null' is the only reachable in the whole switch">null</warning>:
        break;
      case default:
        break;
    }
  }

  void unknownSetNotNullWithNullLabel(Integer i) {
    i = 1;
    switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning>:
        break;
      case null:
        break;
      case default:
        break;
    }
  }

  void unknownSetNullWithoutNullLabel(Integer i) {
    i = null;
    switch (<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>) {
      case 1:
        break;
      case default:
        break;
    }
  }

  void unknownSetNotNullWithoutNullLabel(Integer i) {
    i = 1;
    switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning>:
        break;
      case default:
        break;
    }
  }

  void notNullWithNullLabel(@NotNull Integer i) {
    switch (i) {
      case 1:
        break;
      case <warning descr="Switch label 'null' is unreachable">null</warning>:
        break;
      case default:
        break;
    }
  }

  void notNullWithoutNullLabel(@NotNull Integer i) {
    switch (i) {
      case 1:
        break;
      case default:
        break;
    }
  }

  void notNullSetNullWithNullLabel(@NotNull Integer i) {
    i = null;
    switch (i) {
      case 1:
        break;
      case <warning descr="Switch label 'null' is the only reachable in the whole switch">null</warning>:
        break;
      case default:
        break;
    }
  }

  void notNullSetNotNullWithNullLabel(@NotNull Integer i) {
    i = 1;
    switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning>:
        break;
      case null:
        break;
      case default:
        break;
    }
  }

  void notNullSetNullWithoutNullLabel(@NotNull Integer i) {
    i = null;
    switch (<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>) {
      case 1:
        break;
      case default:
        break;
    }
  }

  void notNullSetNotNullWithoutNullLabel(@NotNull Integer i) {
    i = 1;
    switch (i) {
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning>:
        break;
      case default:
        break;
    }
  }

  void nullableCallWithNullLabel() {
    switch (createNullValue()) {
      case 1:
        break;
      case null:
        break;
      case default:
        break;
    }
  }

  void nullableCallWithoutNullLabel() {
    switch (<warning descr="Unboxing of 'createNullValue()' may produce 'NullPointerException'">createNullValue()</warning>) {
      case 1:
        break;
      case default:
        break;
    }
  }

  void unknownCallWithNullLabel() {
    switch (createValue()) {
      case 1:
        break;
      case null:
        break;
      case default:
        break;
    }
  }

  void unknownCallWithoutNullLabel() {
    switch (createValue()) {
      case 1:
        break;
      case default:
        break;
    }
  }

  void notNullCallWithNullLabel() {
    switch (createNotNullValue()) {
      case 1, 2:
        break;
      case <warning descr="Switch label 'null' is unreachable">null</warning>:
        break;
      case default:
        break;
    }
  }

  void notNullCallWithoutNullLabel() {
    switch (createNotNullValue()) {
      case 1, 2:
        break;
      default:
        break;
    }
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