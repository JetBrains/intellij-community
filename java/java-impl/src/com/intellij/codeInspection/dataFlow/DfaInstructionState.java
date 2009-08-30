/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 9:40:01 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.Instruction;

public class DfaInstructionState {
  public static final DfaInstructionState[] EMPTY_ARRAY = new DfaInstructionState[0];
  private final DfaMemoryState myBeforeMemoryState;
  private final Instruction myInstruction;
  private long myDistanceFromStart = 0;

  public DfaInstructionState(Instruction myInstruction, DfaMemoryState myBeforeMemoryState) {
    this.myBeforeMemoryState = myBeforeMemoryState;
    this.myInstruction = myInstruction;
  }

  public long getDistanceFromStart() { return myDistanceFromStart; }

  public void setDistanceFromStart(long distanceFromStart) { myDistanceFromStart = distanceFromStart; }

  public Instruction getInstruction() {
    return myInstruction;
  }

  public DfaMemoryState getMemoryState() {
    return myBeforeMemoryState;
  }

  public String toString() {
    return "" + getInstruction().getIndex() + ": " + getMemoryState().toString() + " " + getInstruction().toString();
  }
}
