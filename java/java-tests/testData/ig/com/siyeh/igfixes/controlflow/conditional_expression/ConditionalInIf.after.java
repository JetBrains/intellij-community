package com.siyeh.ipp.conditional.withIf;

class ConditionalInIf {
  private Object value;

  public boolean equals(ConditionalInIf that) {
      if (value != null) {
          if (!value.equals(that.value)) {
              return false;
          }
      } else {
          if (that.value != null) {
              return false;
          }
      }

    return true;
  }
}