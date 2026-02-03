package com.siyeh.ipp.switchtoif.replace_if_with_switch;

import java.io.Serializable;

class WithEnums {
  public Serializable eType;

  public String toString() {
    i<caret>f (eType.equals(ETypes.PRINCIPAL)) {
      return "1";
    }
    if (eType.equals(ETypes2.UP)) {
      return "12";
    }
    if (eType.equals(ETypes2.DOWN)) {
      return "123";
    }
    return "1234";
  }

  public enum ETypes implements Serializable {
    PRINCIPAL
  }

  public enum ETypes2 implements Serializable {
    UP, DOWN
  }
}
