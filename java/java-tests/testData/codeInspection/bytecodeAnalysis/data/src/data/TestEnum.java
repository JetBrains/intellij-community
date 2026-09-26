package bytecodeAnalysis.data;

import bytecodeAnalysis.ExpectContract;

public enum TestEnum {
  A, B, C;

  @ExpectContract(pure = true)
  public int getValue() {
    return ordinal()+1;
  }

  @ExpectContract(pure = true)
  public boolean isA() {
    switch (this) {
      case A:
        return true;
      default:
        return false;
    }
  }
}