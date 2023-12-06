package com.siyeh.igtest.initialization.instance_variable_uninitialized_use;

import java.io.IOException;

class InstanceVariableUnitializedUse {
  int i;

  InstanceVariableUnitializedUse() throws IOException {
    try (java.io.FileInputStream in = new java.io.FileInputStream("asdf" + (i=3) + "asdf")) {}
    System.out.println(i);
  }
}

class InstanceFieldVsDoWhile {
  private Object object;

  public InstanceFieldVsDoWhile() {
    do {
      object = new Object();
    } while (object.hashCode() < 1000); // Instance field used before initialization
  }
}

class FinalField {
  private final Object object;

  FinalField() {
    System.out.println(<error descr="Variable 'object' might not have been initialized">object</error>);
    object = null;
  }
}
class SwitchExpression {
  private int i;
  private int j;

  SwitchExpression(E e, int z) {
    int x;
    x = switch (e) {
      case A:
        i = 1;
        yield 2;
      case B:
        yield i = 3;
      case C:
        i = 3;
        yield 4;
    };
    System.out.println(i);

    int p = switch (e) {
      case A:
        yield j = 1;
      case B:
        yield 3;
      case C:
        j = 3;
        yield 4;
    };
    System.out.println(<warning descr="Instance field 'j' used before initialized">j</warning>);
  }

  enum E {
    A, B, C
  }

  SwitchExpression() {
    int z = switch (10) {
      case 1, 2 -> i = 9;
      default -> i = 10;
    };
    System.out.println(i);
  }

  SwitchExpression(boolean b) {
    int x = switch(0) { default -> { { yield i = 2; } } };
    System.out.println("i = " + i);
  }
}
class Lambda {
  int i;
  Lambda() {
    Runnable r = () -> {
      i = 10;
    };
    System.out.println(<warning descr="Instance field 'i' used before initialized">i</warning>);
  }
}
