package com.siyeh.ipp.switchtoif.replace_if_with_switch;

import java.io.Serializable;

class WithEnums {
  public Serializable eType;

  public String toString() {
      <caret>return switch (eType) {
          case ETypes.PRINCIPAL -> "1";
          case ETypes2.UP -> "12";
          case ETypes2.DOWN -> "123";
          default -> "1234";
      };
  }

  public enum ETypes implements Serializable {
    PRINCIPAL
  }

  public enum ETypes2 implements Serializable {
    UP, DOWN
  }
}
