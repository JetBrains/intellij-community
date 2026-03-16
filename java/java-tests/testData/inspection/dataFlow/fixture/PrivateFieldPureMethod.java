package org.example;

import org.jetbrains.annotations.*;

class PrivateFieldPureMethod {
  private @Nullable String myField;

  void test() {
    boolean b = myField != null;
    if (isValid() && b) {
      System.out.println(myField.trim());
    }
  }

  @Contract(pure = true)
  public boolean isValid() {
    return Math.random() > 0.5;
  }
}