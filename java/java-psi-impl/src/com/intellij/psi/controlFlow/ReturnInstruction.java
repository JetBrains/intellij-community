// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;


public class ReturnInstruction extends GoToInstruction {
  private static final Logger LOG = Logger.getInstance(ReturnInstruction.class);

  private @NotNull CallInstruction myCallInstruction;
  private boolean myRethrowFromFinally;

  public ReturnInstruction(int offset, @NotNull CallInstruction callInstruction) {
    super(offset, Role.END, false);
    myCallInstruction = callInstruction;
  }

  @Override
  public String toString() {
    return "RETURN FROM " + getProcBegin() + (offset == 0 ? "" : " TO "+offset);
  }

  int @NotNull [] getPossibleReturnOffsets() {
    return offset == 0 ?
        new int[]{
          getProcBegin() - 5, // call normal
          getProcBegin() - 3, // call return
          getProcBegin() - 1, // call throw
        }
        :
        new int[]{
          offset,    // exit from middle of the finally
        };

  }

  int getProcBegin() {
    return myCallInstruction.procBegin;
  }

  int getProcEnd() {
    return myCallInstruction.procEnd;
  }

  void setCallInstruction(@NotNull CallInstruction callInstruction) {
    myCallInstruction = callInstruction;
  }


  @Override
  public int nNext() { return offset == 0 ? 3 : 1; }

  @Override
  public int getNext(int index, int no) {
    if (offset == 0) {
      switch (no) {
        case 0: return getProcBegin() - 5; // call normal
        case 1: return getProcBegin() - 3; // call return
        case 2: return getProcBegin() - 1; // call throw
        default:
          LOG.assertTrue (false);
          return -1;
      }
    }
    if (no == 0) {
      return offset; // call normal
    }
    LOG.assertTrue(false);
    return -1;
  }

  @Override
  public void accept(@NotNull ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitReturnInstruction(this, offset, nextOffset);
  }

  void setRethrowFromFinally() {
    myRethrowFromFinally = true;
  }

  boolean isRethrowFromFinally() {
    return myRethrowFromFinally;
  }
}
