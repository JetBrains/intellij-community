package com.intellij.java.codeInspection.bytecodeAnalysis.data;

import com.intellij.java.codeInspection.bytecodeAnalysis.ExpectContract;
import com.intellij.java.codeInspection.bytecodeAnalysis.ExpectNotNull;

// Test that indified string concatenation is properly recognized
// This file is precompiled via Java 9 compiler and class file is placed in the same directory
// Compilation command is
// "C:\Program Files\Java\jdk-9.0.1\bin\javac.exe" -cp $PROJECT_DIR$\out\classes\test\intellij.java.tests TestJava9.java
public class TestJava9 {
  @ExpectContract(pure = true)
  @ExpectNotNull
  String concat(String foo, String bar) {
    return foo+"!"+bar;
  }
}