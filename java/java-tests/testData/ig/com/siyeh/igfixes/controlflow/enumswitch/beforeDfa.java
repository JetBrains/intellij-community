// "Create missing branch 'INACTIVE'" "true"
package com.siyeh.ipp.enumswitch;

class BeforeDefault {
  enum Status { ACTIVE, INACTIVE, ERROR }

  private void foo (Status status) {
    if (status == Status.ERROR) return;
    sw<caret>itch (status) {
      case ACTIVE:
        break;
    }
  }
}