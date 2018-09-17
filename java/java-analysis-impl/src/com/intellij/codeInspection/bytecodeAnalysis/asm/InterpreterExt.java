// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis.asm;

/**
 * @author lambdamix
 */
public interface InterpreterExt<Data> {
  // init interpreter state by passing entry data
  void init(Data previous);

  // exit data after execution for edge to insn
  // there are may be different outcomes for different edges if an instruction was branching one
  Data getAfterData(int insn);

  // merge two states
  Data merge(Data data1, Data data2);
}