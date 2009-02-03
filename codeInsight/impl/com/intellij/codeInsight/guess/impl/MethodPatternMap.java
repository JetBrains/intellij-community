
package com.intellij.codeInsight.guess.impl;

import com.intellij.util.containers.HashMap;

class MethodPatternMap {
  private final HashMap myMethodNameToPatternsMap = new HashMap();

  public void addPattern(MethodPattern pattern){
    myMethodNameToPatternsMap.put(pattern.methodName + "#" + pattern.parameterCount, pattern);
  }

  public MethodPattern findPattern(String name, int parameterCount){
    return (MethodPattern)myMethodNameToPatternsMap.get(name + "#" + parameterCount);
  }
}
