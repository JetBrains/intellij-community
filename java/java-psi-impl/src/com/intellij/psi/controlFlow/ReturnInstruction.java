/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;


public class ReturnInstruction extends GoToInstruction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.controlFlow.ReturnInstruction");

  @NotNull private final ControlFlowStack myStack;
  @NotNull private CallInstruction myCallInstruction;
  private boolean myRethrowFromFinally;

  public ReturnInstruction(int offset, @NotNull ControlFlowStack stack, @NotNull CallInstruction callInstruction) {
    super(offset, Role.END, false);
    myStack = stack;
    myCallInstruction = callInstruction;
  }

  public String toString() {
    return "RETURN FROM " + getProcBegin() + (offset == 0 ? "" : " TO "+offset);
  }

  public int execute(boolean pushBack) {
    synchronized (myStack) {
      int jumpTo = -1;
      if (myStack.size() != 0) {
        jumpTo = myStack.pop(pushBack);
      }
      if (offset != 0) {
        jumpTo = offset;
      }
      return jumpTo;
    }
  }

  @NotNull
  int[] getPossibleReturnOffsets() {
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
    if (offset == 0)
      switch (no) {
        case 0: return getProcBegin() - 5; // call normal
        case 1: return getProcBegin() - 3; // call return
        case 2: return getProcBegin() - 1; // call throw
        default:
          LOG.assertTrue (false);
          return -1;
      }
    else
      switch (no) {
        case 0: return offset; // call normal
        default:
          LOG.assertTrue (false);
          return -1;
      }
  }

  @Override
  public void accept(@NotNull ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitReturnInstruction(this, offset, nextOffset);
  }

  @NotNull
  public ControlFlowStack getStack() {
    return myStack;
  }

  void setRethrowFromFinally() {
    myRethrowFromFinally = true;
  }

  boolean isRethrowFromFinally() {
    return myRethrowFromFinally;
  }
}
