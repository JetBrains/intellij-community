package com.siyeh.igtest.controlflow.switch_statements_without_default;

class TrueFalsePatterns {
  static void foo(Boolean b) {
    switch (b) {
      case true -> System.out.println("It's true");
      case false -> System.out.println("It's false");
    }
  }
}
