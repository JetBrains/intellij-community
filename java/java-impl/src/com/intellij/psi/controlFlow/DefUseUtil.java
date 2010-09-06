/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Mar 22, 2002
 * Time: 7:25:02 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.containers.Queue;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DefUseUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.defUse.DefUseUtil");

  private DefUseUtil() {
  }

  public static class Info {
    private final PsiVariable myVariable;
    private final PsiElement myContext;
    private final boolean myIsRead;

    public Info(PsiVariable variable, PsiElement context, boolean read) {
      myVariable = variable;
      myContext = context;
      myIsRead = read;
    }

    public PsiVariable getVariable() {
      return myVariable;
    }

    public PsiElement getContext() {
      return myContext;
    }

    public boolean isRead() {
      return myIsRead;
    }
  }

  private static class InstructionState {
    private Set<PsiVariable> myVariablesUseArmed;
    private final int myInstructionIdx;
    private final IntArrayList myBackwardTraces;
    private boolean myIsVisited = false;

    public InstructionState(int instructionIdx) {
      myInstructionIdx = instructionIdx;
      myBackwardTraces = new IntArrayList();
      myVariablesUseArmed = null;
    }

    public void addBackwardTrace(int i) {
      myBackwardTraces.add(i);
    }

    public IntArrayList getBackwardTraces() {
      return myBackwardTraces;
    }

    public int getInstructionIdx() {
      return myInstructionIdx;
    }

    void mergeUseArmed(PsiVariable psiVariable) {
      touch();
      myVariablesUseArmed.add(psiVariable);
    }

    boolean mergeUseDisarmed(PsiVariable psiVariable) {
      touch();

      boolean result = myVariablesUseArmed.contains(psiVariable);
      myVariablesUseArmed.remove(psiVariable);

      return result;
    }

    private void touch() {
      if (myVariablesUseArmed == null) myVariablesUseArmed = new THashSet<PsiVariable>();
    }

    public void merge(InstructionState state) {
      touch();
      myVariablesUseArmed.addAll(state.myVariablesUseArmed);
    }

    public boolean contains(InstructionState state) {
      return myVariablesUseArmed != null && state.myVariablesUseArmed != null &&
             myVariablesUseArmed.containsAll(state.myVariablesUseArmed);
    }

    public boolean markVisited() {
      boolean old = myIsVisited;
      myIsVisited = true;
      return old;
    }

    public boolean isVisited() {
      return myIsVisited;
    }
  }

  public static List<Info> getUnusedDefs(PsiCodeBlock body, Set<PsiVariable> outUsedVariables) {
    if (body == null) {
      return null;
    }
    List<Info> unusedDefs = new ArrayList<Info>();

    ControlFlow flow;
    try {
      flow = ControlFlowFactory.getInstance(body.getProject()).getControlFlow(body, ourPolicy);
    }
    catch (AnalysisCanceledException e) {
      return null;
    }
    List<Instruction> instructions = flow.getInstructions();
    if (LOG.isDebugEnabled()) {
      LOG.debug(flow.toString());
    }

    Set<PsiVariable> assignedVariables = new THashSet<PsiVariable>();
    Set<PsiVariable> readVariables = new THashSet<PsiVariable>();
    for (int i = 0; i < instructions.size(); i++) {
      Instruction instruction = instructions.get(i);
      ProgressManager.checkCanceled();
      if (instruction instanceof WriteVariableInstruction) {
        WriteVariableInstruction writeInstruction = (WriteVariableInstruction)instruction;
        PsiElement context = flow.getElement(i);
        context = PsiTreeUtil.getParentOfType(context, PsiStatement.class, false);
        PsiVariable psiVariable = writeInstruction.variable;
        if (context != null && !(context instanceof PsiDeclarationStatement && psiVariable.getInitializer() == null)) {
          assignedVariables.add(psiVariable);
        }
      }
      else if (instruction instanceof ReadVariableInstruction) {
        ReadVariableInstruction readInstruction = (ReadVariableInstruction)instruction;
        readVariables.add(readInstruction.variable);
      }
    }

    InstructionState[] states = getStates(instructions);

    boolean[] defsArmed = new boolean[instructions.size()];

    Queue<InstructionState> queue = new Queue<InstructionState>(8);

    for (int i = states.length - 1; i >= 0; i--) {
      final InstructionState outerState = states[i];
      if (outerState.isVisited()) continue;
      outerState.touch();

      for (PsiVariable psiVariable : assignedVariables) {
        if (psiVariable instanceof PsiField) {
          outerState.mergeUseArmed(psiVariable);
        }
      }
      queue.addLast(outerState);

      while (!queue.isEmpty()) {
        ProgressManager.checkCanceled();
        InstructionState state = queue.pullFirst();
        state.markVisited();

        int idx = state.getInstructionIdx();
        if (idx < instructions.size()) {
          Instruction instruction = instructions.get(idx);

          if (instruction instanceof WriteVariableInstruction) {
            WriteVariableInstruction writeInstruction = (WriteVariableInstruction)instruction;
            PsiVariable psiVariable = writeInstruction.variable;
            outUsedVariables.add(psiVariable);
            if (state.mergeUseDisarmed(psiVariable)) {
              defsArmed[idx] = true;
            }
          }
          else if (instruction instanceof ReadVariableInstruction) {
            ReadVariableInstruction readInstruction = (ReadVariableInstruction)instruction;
            state.mergeUseArmed(readInstruction.variable);
            outUsedVariables.add(readInstruction.variable);
          }
          else {
            state.touch();
          }
        }

        IntArrayList backwardTraces = state.getBackwardTraces();
        for (int j = 0; j < backwardTraces.size(); j++) {
          int prevIdx = backwardTraces.get(j);
          InstructionState prevState = states[prevIdx];
          if (!prevState.contains(state)) {
            prevState.merge(state);
            queue.addLast(prevState);
          }
        }
      }
    }

    for (int i = 0; i < instructions.size(); i++) {
      Instruction instruction = instructions.get(i);
      if (instruction instanceof WriteVariableInstruction) {
        WriteVariableInstruction writeInstruction = (WriteVariableInstruction)instruction;
        if (!defsArmed[i]) {
          PsiElement context = flow.getElement(i);
          context = PsiTreeUtil.getNonStrictParentOfType(context, PsiStatement.class, PsiAssignmentExpression.class,
                                                         PsiPostfixExpression.class, PsiPrefixExpression.class);
          PsiVariable psiVariable = writeInstruction.variable;
          if (context != null && !(context instanceof PsiTryStatement)) {
            if (context instanceof PsiDeclarationStatement && psiVariable.getInitializer() == null) {
              if (!assignedVariables.contains(psiVariable)) {
                unusedDefs.add(new Info(psiVariable, context, false));
              }
            }
            else {
              unusedDefs.add(new Info(psiVariable, context, readVariables.contains(psiVariable)));
            }
          }
        }
      }
    }

    return unusedDefs;

  }

  @NotNull
  public static PsiElement[] getDefs(PsiCodeBlock body, final PsiVariable def, PsiElement ref) {
    try {
      RefsDefs refsDefs = new RefsDefs(body) {
        private final InstructionState[] states = getStates(instructions);

        protected int nNext(int index) {
          return states[index].getBackwardTraces().size();
        }

        protected int getNext(int index, int no) {
          return states[index].getBackwardTraces().get(no);
        }

        protected boolean defs() {
          return true;
        }

        protected void processInstruction(final Set<PsiElement> res, final Instruction instruction, int index) {
          if (instruction instanceof WriteVariableInstruction) {
            WriteVariableInstruction instructionW = (WriteVariableInstruction)instruction;
            if (instructionW.variable == def) {

              final PsiElement element = flow.getElement(index);
              element.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitReferenceExpression(PsiReferenceExpression ref) {
                  if (PsiUtil.isAccessedForWriting(ref)) {
                    if (ref.resolve() == def) {
                      res.add(ref);
                    }
                  }
                }

                @Override
                public void visitVariable(PsiVariable var) {
                  if (var == def && (var instanceof PsiParameter || var.hasInitializer())) {
                    res.add(var);
                  }
                }
              });
            }
          }
        }
      };
      return refsDefs.get(def, ref);
    }
    catch (AnalysisCanceledException e) {
      return PsiElement.EMPTY_ARRAY;
    }
  }

  @NotNull
  public static PsiElement[] getRefs(PsiCodeBlock body, final PsiVariable def, PsiElement ref) {
    try {
      RefsDefs refsDefs = new RefsDefs(body) {
        protected int nNext(int index) {
          return instructions.get(index).nNext();
        }

        protected int getNext(int index, int no) {
          return instructions.get(index).getNext(index, no);
        }

        protected boolean defs() {
          return false;
        }

        protected void processInstruction(final Set<PsiElement> res, final Instruction instruction, int index) {
          if (instruction instanceof ReadVariableInstruction) {
            ReadVariableInstruction instructionR = (ReadVariableInstruction)instruction;
            if (instructionR.variable == def) {

              final PsiElement element = flow.getElement(index);
              element.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitReferenceExpression(PsiReferenceExpression ref) {
                  if (ref.resolve() == def) {
                    res.add(ref);
                  }
                }
              });
            }
          }
        }
      };
      return refsDefs.get(def, ref);
    }
    catch (AnalysisCanceledException e) {
      return PsiElement.EMPTY_ARRAY;
    }
  }

  private abstract static class RefsDefs {
    protected abstract int   nNext(int index);
    protected abstract int getNext(int index, int no);

    final List<Instruction> instructions;
    final ControlFlow flow;
    final PsiCodeBlock body;


    protected RefsDefs(PsiCodeBlock body) throws AnalysisCanceledException {
      this.body = body;
      flow = ControlFlowFactory.getInstance(body.getProject()).getControlFlow(body, ourPolicy);
      instructions = flow.getInstructions();
    }

    protected abstract void processInstruction(Set<PsiElement> res, final Instruction instruction, int index);
    protected abstract boolean defs ();

    @NotNull
    private PsiElement[] get (final PsiVariable def, PsiElement refOrDef) {
      if (body == null) {
        return PsiElement.EMPTY_ARRAY;
      }

      final boolean [] visited = new boolean[instructions.size() + 1];
      visited [visited.length-1] = true; // stop on the code end
      int elem = defs() ? flow.getStartOffset(refOrDef) : flow.getEndOffset(refOrDef);

      // hack: ControlFlow doesn't contains parameters initialization
      if (elem == -1 && def instanceof PsiParameter) {
        elem = 0;
      }

      if (elem != -1) {
        if (!defs () && instructions.get(elem) instanceof ReadVariableInstruction) {
          LOG.assertTrue(nNext(elem) == 1);
          LOG.assertTrue(getNext(elem,0) == elem+1);
          elem += 1;
        }

        final Set<PsiElement> res = new THashSet<PsiElement>();
        class Inner {

          void traverse (int index) {
            visited [index] = true;

            if (defs ()) {
              final Instruction instruction = instructions.get(index);
              processInstruction(res, instruction, index);
              if (instruction instanceof WriteVariableInstruction) {
                WriteVariableInstruction instructionW = (WriteVariableInstruction)instruction;
                if (instructionW.variable == def) {
                  return;
                }
              }

              // hack: ControlFlow doesnn't contains parameters initialization
              if (index == 0 && def instanceof PsiParameter) {
                res.add(def.getNameIdentifier());
              }
            }

            final int nNext = nNext (index);
            for (int i = 0; i < nNext; i++) {
              final int prev = getNext(index, i);
              if (!visited [prev]) {
                if (!defs ()) {
                  final Instruction instruction = instructions.get(prev);
                  if (instruction instanceof WriteVariableInstruction) {
                    WriteVariableInstruction instructionW = (WriteVariableInstruction)instruction;
                    if (instructionW.variable == def) {
                      continue;
                    }
                  } else {
                    processInstruction(res, instruction, prev);
                  }
                }
                traverse (prev);

              }
            }
          }
        }
        new Inner ().traverse (elem);
        return res.toArray(new PsiElement[res.size ()]);
      }
      return PsiElement.EMPTY_ARRAY;
    }
  }


  private static InstructionState[] getStates(final List<Instruction> instructions) {
    final InstructionState[] states = new InstructionState[instructions.size()];
    for (int i = 0; i < states.length; i++) {
      states[i] = new InstructionState(i);
    }

    for (int i = 0; i < instructions.size(); i++) {
      final Instruction instruction = instructions.get(i);
      for (int j = 0; j != instruction.nNext(); ++ j) {
        final int next = instruction.getNext(i, j);
        if (next < states.length) {
          states[next].addBackwardTrace(i);
        }
      }
    }
    return states;
  }

  private static final ControlFlowPolicy ourPolicy = new ControlFlowPolicy() {
    public PsiVariable getUsedVariable(PsiReferenceExpression refExpr) {
      if (refExpr.isQualified()) return null;

      PsiElement refElement = refExpr.resolve();
      if (refElement instanceof PsiLocalVariable || refElement instanceof PsiParameter) {
        return (PsiVariable) refElement;
      }

      return null;
    }

    public boolean isParameterAccepted(PsiParameter psiParameter) {
      return true;
    }

    public boolean isLocalVariableAccepted(PsiLocalVariable psiVariable) {
      return true;
    }
  };

}
