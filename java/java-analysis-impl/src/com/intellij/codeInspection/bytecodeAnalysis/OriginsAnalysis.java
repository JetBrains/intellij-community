/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.bytecodeAnalysis;

import gnu.trove.TIntArrayList;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode;
import org.jetbrains.org.objectweb.asm.tree.InsnList;
import org.jetbrains.org.objectweb.asm.tree.analysis.Value;
import org.jetbrains.org.objectweb.asm.tree.analysis.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * @author lambdamix
 */
public class OriginsAnalysis {

  final static boolean debug = false;

  private static SourceInterpreter myInterpreter = new SourceInterpreter() {
    @Override
    public SourceValue copyOperation(AbstractInsnNode insn, SourceValue value) {
      return value;
    }
  };

  private static class MyValue extends SourceValue {
    final boolean local;
    final int slot;

    public MyValue(boolean local, int slot, int size) {
      super(size);
      this.local = local;
      this.slot = slot;
    }
  }

  private static class Location {
    final boolean local;
    final int slot;

    Location(boolean local, int slot) {
      this.local = local;
      this.slot = slot;
    }

    @Override
    public String toString() {
      return "Location{" +
             "local=" + local +
             ", slot=" + slot +
             '}';
    }
  }

  private static class ILocation {
    final boolean local;

    @Override
    public String toString() {
      return "ILocation{" +
             "local=" + local +
             ", insnIndex=" + insnIndex +
             ", slot=" + slot +
             '}';
    }

    final int insnIndex;
    final int slot;

    private ILocation(boolean local, int insnIndex, int slot) {
      this.local = local;
      this.insnIndex = insnIndex;
      this.slot = slot;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null) return false;
      ILocation iLocation = (ILocation)o;
      if (local != iLocation.local) return false;
      if (insnIndex != iLocation.insnIndex) return false;
      if (slot != iLocation.slot) return false;
      return true;
    }

    @Override
    public int hashCode() {
      // +1 is to avoid collisions
      int result = 31 * insnIndex + slot + 1;
      return local ? result : -result;
    }
  }

  public static boolean[] resultOrigins(Frame<Value>[] frames, InsnList instructions, ControlFlowGraph graph) throws AnalyzerException {

    TIntArrayList[] backTransitions = new TIntArrayList[instructions.size()];
    for (int i = 0; i < backTransitions.length; i++) {
      backTransitions[i] = new TIntArrayList();
    }
    LinkedList<ILocation> queue = new LinkedList<ILocation>();
    HashSet<ILocation> queued = new HashSet<ILocation>();
    for (int from = 0; from < instructions.size(); from++) {
      for (int to : graph.transitions[from]) {
        TIntArrayList froms = backTransitions[to];
        froms.add(from);
        int opcode = instructions.get(to).getOpcode();
        if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.ARETURN) {
          ILocation sourceLoc = new ILocation(false, from, frames[to].getStackSize() - 1);
          if (queued.add(sourceLoc)) {
            queue.push(sourceLoc);
          }
        }
      }
      if (debug) {
        System.err.println(from + " " + Arrays.toString(graph.transitions[from]));
      }
    }

    if (debug) {
      System.err.println("***");
      for (int i = 0; i < backTransitions.length; i++) {
        System.err.println(i + " " + backTransitions[i]);
      }
    }

    boolean[] result = new boolean[instructions.size()];
    while (!queue.isEmpty()) {
      ILocation resultLocation = queue.pop();

      int insnIndex = resultLocation.insnIndex;
      AbstractInsnNode insn = instructions.get(insnIndex);
      int opcode = insn.getOpcode();
      Location preLocation = traceSource(frames[insnIndex], resultLocation, insn);
      if (debug) {
        System.err.println("location");
        System.err.println(resultLocation);
        System.err.println(opcode);
        System.err.println("pre-location");
        System.err.println(preLocation);
      }
      if (preLocation == null) {
        if (opcode != Opcodes.INVOKEINTERFACE && opcode != Opcodes.GETFIELD && !(opcode >= Opcodes.IALOAD && opcode <= Opcodes.SALOAD)) {
          result[insnIndex] = true;
        }
      } else {
        TIntArrayList froms = backTransitions[insnIndex];
        for (int i = 0; i < froms.size(); i++) {
          ILocation preILoc = new ILocation(preLocation.local, froms.getQuick(i), preLocation.slot);
          if (queued.add(preILoc)) {
            if (debug) {
              System.err.println("queuing");
              System.err.println(preILoc);
            }
            queue.push(preILoc);
          }
        }
      }
    }


    if (debug) {
      System.err.println(Arrays.toString(result));
    }

    return result;
  }

  private static Frame<SourceValue> makePreFrame(Frame<Value> frame) {
    Frame<SourceValue> preFrame = new Frame<SourceValue>(frame.getLocals(), frame.getMaxStackSize());
    for (int i = 0; i < frame.getLocals(); i++) {
       preFrame.setLocal(i, new MyValue(true, i, frame.getLocal(i).getSize()));
    }
    for (int i = 0; i < frame.getStackSize(); i++) {
      preFrame.push(new MyValue(false, i, frame.getStack(i).getSize()));
    }
    return preFrame;
  }

  private static Location traceSource(Frame<Value> postFrame, ILocation result, AbstractInsnNode insn) throws AnalyzerException {
    Location theSameLocation = new Location(result.local, result.slot);
    int insnType = insn.getType();

    if (insnType == AbstractInsnNode.LABEL || insnType == AbstractInsnNode.LINE || insnType == AbstractInsnNode.FRAME) {
      return theSameLocation;
    }
    int opCode = insn.getOpcode();
    if (result.local && !((opCode >= Opcodes.ISTORE && opCode <= Opcodes.ASTORE) || opCode == Opcodes.IINC)) {
      return theSameLocation;
    }
    Frame<SourceValue> preFrame = makePreFrame(postFrame);
    preFrame.execute(insn, myInterpreter);
    if (result.local) {
      SourceValue preVal = preFrame.getLocal(result.slot);
      if (preVal instanceof MyValue) {
        MyValue val = (MyValue)preVal;
        return new Location(val.local, val.slot);
      }
    } else {
      SourceValue preVal = preFrame.getStack(result.slot);
      if (preVal instanceof MyValue) {
        MyValue val = (MyValue)preVal;
        return new Location(val.local, val.slot);
      }
    }
    return null;
  }
}


