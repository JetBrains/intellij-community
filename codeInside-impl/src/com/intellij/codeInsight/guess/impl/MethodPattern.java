
package com.intellij.codeInsight.guess.impl;

class MethodPattern{
  public final String methodName;
  public final int parameterCount;
  public final int parameterIndex; // -1 for return type

  public MethodPattern(String methodName, int parameterCount, int parameterIndex) {
    this.methodName = methodName;
    this.parameterCount = parameterCount;
    this.parameterIndex = parameterIndex;
  }
}