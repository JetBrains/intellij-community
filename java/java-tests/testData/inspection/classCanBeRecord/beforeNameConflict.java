// "Convert to a record" "true"
package com.example;

interface Runnable {}

class <caret>X implements Runnable {
  private final int field;

  X(int field) {
    this.field = field;
  }

  public int field() {
    return field;
  }
}