/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Sep 20, 2002
 */
package com.intellij.psi.controlFlow;

import com.intellij.util.containers.IntArrayList;

import java.util.ArrayList;

public class ControlFlowStack {
  private final IntArrayList myIpStack = new IntArrayList();
  private final ArrayList<CallInstruction> myCallInstructionStack = new ArrayList<CallInstruction>();

  public void push(int ip, CallInstruction callInstruction) {
    myIpStack.add(ip);
    myCallInstructionStack.add(callInstruction);
  }

  public int pop(boolean pushBack) {
    final int i = myIpStack.get(myIpStack.size() - 1);
    if (!pushBack) {
      myIpStack.remove(myIpStack.size()-1);
      myCallInstructionStack.remove(myCallInstructionStack.size()-1);
    }
    return i;
  }
  public int peekReturnOffset() {
    return myIpStack.get(myIpStack.size() - 1);
  }
  public int size() {
    return myIpStack.size();
  }

}
