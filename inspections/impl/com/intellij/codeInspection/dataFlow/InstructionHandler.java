/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.dataFlow;

/**
 * @author Gregory.Shrago
 */
public interface InstructionHandler<T extends DataFlowRunner, S extends DfaMemoryState> {
  S createEmptyMemoryState(final T dataFlowRunner);
}
