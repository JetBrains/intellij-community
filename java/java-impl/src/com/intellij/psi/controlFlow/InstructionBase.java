/**
 * @author cdr
 */
package com.intellij.psi.controlFlow;

import org.jetbrains.annotations.NonNls;

public abstract class InstructionBase implements Instruction, Cloneable{
  public Instruction clone() {
    try {
      return (Instruction)super.clone();
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }

  @NonNls
  public abstract String toString();

}