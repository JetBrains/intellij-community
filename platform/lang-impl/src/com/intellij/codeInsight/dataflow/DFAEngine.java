/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.codeInsight.dataflow;

import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;

import java.util.*;

public class DFAEngine<E> {
  private static final Logger LOG = Logger.getInstance(DFAEngine.class.getName());
  private static final double TIME_LIMIT = 3*10e9;

  private final Instruction[] myFlow;

  private final DfaInstance<E> myDfa;
  private final Semilattice<E> mySemilattice;

  public DFAEngine(final Instruction[] flow,
                   final DfaInstance<E> dfa,
                   final Semilattice<E> semilattice) {
    myFlow = flow;
    myDfa = dfa;
    mySemilattice = semilattice;
  }


  public List<E> performDFA() {
    final ArrayList<E> info = new ArrayList<E>(myFlow.length);
    return performDFA(info);
  }

  public List<E> performDFA(final List<E> info) {
    if (LOG.isDebugEnabled()){
      LOG.debug("Perfoming DFA\n" + "Instance: "  + myDfa + " Semilattice: " + mySemilattice);
    }

// initializing dfa
    final E initial = myDfa.initial();
    for (int i = 0; i < myFlow.length; i++) {
      info.add(i, initial);
    }

    final boolean[] visited = new boolean[myFlow.length];

    final boolean forward = myDfa.isForward();
    final int[] order = ControlFlowUtil.postOrder(myFlow);

// Count limit for number of iterations per worklist
    final int limit = getIterationLimit(forward);
    int dfaCount = 0;
    final long startTime = System.nanoTime();

    for (int i = forward ? 0 : myFlow.length - 1; forward ? i < myFlow.length : i >= 0;) {
      // Check time limit only in EDT
      if (!ApplicationManager.getApplication().isUnitTestMode() && 
          ApplicationManager.getApplication().isDispatchThread() &&
          System.nanoTime() - startTime > TIME_LIMIT){
        if (LOG.isDebugEnabled()){
          LOG.debug("Time limit exceeded");
        }
        break;
      }

      // Iteration count per one worklist
      int count = 0;
      final Instruction instruction = myFlow[order[i]];
      final int number = instruction.num();

      if (!visited[number]) {
        final Queue<Instruction> worklist = new LinkedList<Instruction>();
        worklist.add(instruction);
        visited[number] = true;

        // It is essential to apply this check!!!
        // This gives us more chances that resulting info will be closer to expected result
        // Also it is used as indicator that "equals" method is implemented correctly in E
        while (true) {
          // Check if canceled
          ProgressManager.checkCanceled();

          count++;
          if (count > limit){
             if (LOG.isDebugEnabled()){
               LOG.debug("Iteration count exceeded on worklist");
             }
             break;
          }

          final Instruction currentInstruction = worklist.poll();
          if (currentInstruction == null) {
            break;
          }

          final int currentNumber = currentInstruction.num();
          final E oldE = info.get(currentNumber);
          final E joinedE = join(currentInstruction, info);
          final E newE = myDfa.fun(joinedE, currentInstruction);
          if (!mySemilattice.eq(newE, oldE)) {
            if (LOG.isDebugEnabled()){
              LOG.debug("Number: " + currentNumber + " old: " + oldE.toString() + " new: " + newE.toString());
            }
            info.set(currentNumber, newE);
            for (Instruction next : getNext(currentInstruction)) {
              worklist.add(next);
              visited[next.num()] = true;
            }
          }
        }
      }

      // Move to another worklist
      if (forward) {
        i++;
      } else {
        i--;
      }
      dfaCount += count;
    }
    if (LOG.isDebugEnabled()){
      LOG.debug("Done in: " + (System.nanoTime() - startTime)/10e6 + "ms. Ratio: " + dfaCount / myFlow.length);
    }
    return info;
  }


  /**
   * Count limit for dfa number of iterations.
   * Every node in dfa should be processed <= pred times * 2
   * Multiplier 2 is because of cycles.
   */
  private int getIterationLimit(final boolean forward) {
    int allPred = myFlow.length;
    for (Instruction instruction : myFlow) {
      allPred += forward ? instruction.allPred().size() : instruction.allSucc().size();
    }
    return allPred * 2;
  }

  private E join(final Instruction instruction, final List<E> info) {
    final Iterable<? extends Instruction> prev = myDfa.isForward() ? instruction.allPred() : instruction.allSucc();
    final ArrayList<E> prevInfos = new ArrayList<E>();
    for (Instruction i : prev) {
      prevInfos.add(info.get(i.num()));
    }
    return mySemilattice.join(prevInfos);
  }

  private Collection<Instruction> getNext(final Instruction curr) {
    return myDfa.isForward() ? curr.allSucc() : curr.allPred();
  }
}