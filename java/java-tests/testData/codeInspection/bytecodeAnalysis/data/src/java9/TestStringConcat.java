package bytecodeAnalysis.java9;

import bytecodeAnalysis.*;

// Test that indified string concatenation is properly recognized
// This file is precompiled via Java 9 compiler and class file is placed in the same directory
public class TestStringConcat {
  @ExpectContract(pure = true)
  @ExpectNotNull
  String concat(String foo, String bar) {
    return foo+"!"+bar;
  }
}