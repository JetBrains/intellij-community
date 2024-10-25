// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.Stack;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Utility to find current variable value/where this value is read
 */
public final class DefUseUtil {
  private static final Logger LOG = Logger.getInstance(DefUseUtil.class);

  private DefUseUtil() { }

  public static class Info {
    private final @NotNull PsiVariable myVariable;
    private final @NotNull PsiElement myContext;
    private final boolean myIsRead;

    private final boolean myWriteOutsideDeclaration;

    public Info(@NotNull PsiVariable variable, @NotNull PsiElement context, boolean read, boolean writeOutsideDeclaration) {
      myVariable = variable;
      myContext = context;
      myIsRead = read;
      myWriteOutsideDeclaration = writeOutsideDeclaration;
    }

    public @NotNull PsiVariable getVariable() {
      return myVariable;
    }

    public @NotNull PsiElement getContext() {
      return myContext;
    }

    public boolean isRead() {
      return myIsRead;
    }

    /**
     * @return true if this variable was written at least once (except declaration).
     */
    public boolean isWriteOutsideDeclaration() {
      return myWriteOutsideDeclaration;
    }
  }

  private static class InstructionState implements Comparable<InstructionState> {
    private Set<PsiVariable> myUsed;
    private final @NotNull InstructionKey myInstructionKey;
    private final List<InstructionKey> myBackwardTraces;
    private boolean myIsVisited;

    InstructionState(@NotNull InstructionKey instructionKey) {
      myInstructionKey = instructionKey;
      myBackwardTraces = new ArrayList<>(2);
      myUsed = null;
    }

    void addBackwardTrace(@NotNull InstructionKey key) {
      myBackwardTraces.add(key);
    }

    @NotNull
    List<InstructionKey> getBackwardTraces() {
      return myBackwardTraces;
    }

    @NotNull
    InstructionKey getInstructionKey() {
      return myInstructionKey;
    }

    void addUsed(@NotNull PsiVariable psiVariable) {
      touch();
      myUsed.add(psiVariable);
    }

    boolean removeUsed(PsiVariable psiVariable) {
      touch();
      return myUsed.remove(psiVariable);
    }

    private void touch() {
      if (myUsed == null) {
        myUsed = new HashSet<>();
      }
    }

    void addUsedFrom(InstructionState state) {
      touch();
      myUsed.addAll(state.myUsed);
    }

    public boolean contains(InstructionState state) {
      return myUsed != null && state.myUsed != null &&
             myUsed.containsAll(state.myUsed);
    }

    void markVisited() {
      myIsVisited = true;
    }

    public boolean isVisited() {
      return myIsVisited;
    }

    @Override
    public int compareTo(@NotNull InstructionState other) {
      return myInstructionKey.compareTo(other.myInstructionKey);
    }

    @Override
    public String toString() {
      return myInstructionKey + " " + myBackwardTraces + (myIsVisited ? "(v)" : "(n)") + " " + (myUsed != null ? myUsed : "-");
    }
  }

  public static @Nullable List<Info> getUnusedDefs(PsiElement body, Set<? super PsiVariable> outUsedVariables) {
    if (body == null) {
      return null;
    }

    ControlFlow flow;
    try {
      flow = ControlFlowFactory.getControlFlow(body, ourPolicy, ControlFlowOptions.create(true, false, false));
    }
    catch (AnalysisCanceledException e) {
      return null;
    }
    List<Instruction> instructions = flow.getInstructions();
    if (LOG.isDebugEnabled()) {
      LOG.debug(flow.toString());
    }

    Set<PsiVariable> assignedVariables = new HashSet<>();
    Set<PsiVariable> assignedSeveralTimes = new HashSet<>();
    Set<PsiVariable> readVariables = new HashSet<>();
    for (int i = 0; i < instructions.size(); i++) {
      Instruction instruction = instructions.get(i);
      ProgressManager.checkCanceled();
      if (instruction instanceof WriteVariableInstruction) {
        WriteVariableInstruction writeInstruction = (WriteVariableInstruction)instruction;
        PsiElement context = flow.getElement(i);
        context = PsiTreeUtil.getParentOfType(context, PsiStatement.class, false);
        PsiVariable psiVariable = writeInstruction.variable;
        if (context != null && !(context instanceof PsiDeclarationStatement && psiVariable.getInitializer() == null)) {
          if (!assignedVariables.add(psiVariable)) {
            assignedSeveralTimes.add(psiVariable);
          }
        }
      }
      else if (instruction instanceof ReadVariableInstruction) {
        ReadVariableInstruction readInstruction = (ReadVariableInstruction)instruction;
        readVariables.add(readInstruction.variable);
      }
    }

    Map<InstructionKey, InstructionState> stateMap;
    try {
      stateMap = InstructionStateWalker.getStates(instructions);
    }
    catch (InstructionKey.OverflowException e) {
      LOG.error("Failed to compute paths in the control flow graph", e, flow.toString());
      return null;
    }
    InstructionState[] states = stateMap.values().toArray(new InstructionState[0]);
    Arrays.sort(states);

    BitSet usefulWrites = new BitSet(instructions.size());

    Deque<InstructionState> queue = new ArrayDeque<>(8);

    for (int i = states.length - 1; i >= 0; i--) {
      final InstructionState outerState = states[i];
      if (outerState.isVisited()) continue;
      outerState.touch();

      for (PsiVariable psiVariable : assignedVariables) {
        if (psiVariable instanceof PsiField) {
          outerState.addUsed(psiVariable);
        }
      }
      queue.addLast(outerState);

      while (!queue.isEmpty()) {
        ProgressManager.checkCanceled();
        InstructionState state = queue.removeFirst();
        state.markVisited();

        InstructionKey key = state.getInstructionKey();
        if (key.getOffset() < instructions.size()) {
          Instruction instruction = instructions.get(key.getOffset());

          if (instruction instanceof WriteVariableInstruction) {
            WriteVariableInstruction writeInstruction = (WriteVariableInstruction)instruction;
            PsiVariable psiVariable = writeInstruction.variable;
            outUsedVariables.add(psiVariable);
            if (state.removeUsed(psiVariable)) {
              usefulWrites.set(key.getOffset());
            }
          }
          else if (instruction instanceof ReadVariableInstruction) {
            ReadVariableInstruction readInstruction = (ReadVariableInstruction)instruction;
            state.addUsed(readInstruction.variable);
            outUsedVariables.add(readInstruction.variable);
          }
          else {
            state.touch();
          }
        }

        List<InstructionKey> backwardTraces = state.getBackwardTraces();
        for (InstructionKey prevKeys : backwardTraces) {
          InstructionState prevState = stateMap.get(prevKeys);
          if (prevState != null && !prevState.contains(state)) {
            prevState.addUsedFrom(state);
            queue.addLast(prevState);
          }
        }
      }
    }

    List<Info> unusedDefs = new ArrayList<>();

    for (int i = 0; i < instructions.size(); i++) {
      Instruction instruction = instructions.get(i);
      if (instruction instanceof WriteVariableInstruction) {
        WriteVariableInstruction writeInstruction = (WriteVariableInstruction)instruction;
        if (!usefulWrites.get(i)) {
          PsiElement context = PsiTreeUtil.getNonStrictParentOfType(flow.getElement(i),
                                                                    PsiStatement.class, PsiAssignmentExpression.class,
                                                                    PsiUnaryExpression.class);
          PsiVariable psiVariable = writeInstruction.variable;
          if (context != null) {
            if (context instanceof PsiDeclarationStatement && psiVariable.getInitializer() == null) {
              if (!assignedVariables.contains(psiVariable)) {
                unusedDefs.add(new Info(psiVariable, context, false, assignedSeveralTimes.contains(psiVariable)));
              }
            }
            else {
              unusedDefs.add(new Info(psiVariable, context, readVariables.contains(psiVariable), assignedSeveralTimes.contains(psiVariable)));
            }
          }
        }
      }
    }

    return unusedDefs;
  }

  /**
   * Retrieves value of a variable {@code def} at the place {@code ref} in the scope {@code body} 
   * @param def        variable which value is to be defined
   * @param ref        element which contains a reference to the variable {@code def} and where the variable's value is to be defined
   *                   
   * @return variable {@code def} initializers which should be used when inlining {@code ref}
   *         when array length is more than 1, it's unclear what initializer to use and such results are normally rejected
   */
  public static PsiElement @NotNull [] getDefs(@NotNull PsiCodeBlock body, @NotNull PsiVariable def, @NotNull PsiElement ref) {
    return getDefs(body, def, ref, false);
  }

  /**
   * Retrieves value of a variable {@code def} at the place {@code ref} in the scope {@code body} 
   * @param def        variable which value is to be defined
   * @param ref        element which contains a reference to the variable {@code def} and where the variable's value is to be defined
   *                   
   * @return variable {@code def} initializers which should be used when inlining {@code ref}
   *         when array length is more than 1, it's unclear what initializer to use and such results are normally rejected
   */
  public static PsiElement @NotNull [] getDefs(@NotNull PsiCodeBlock body, @NotNull PsiVariable def, @NotNull PsiElement ref, boolean rethrow) {
     if (def instanceof PsiLocalVariable && ref instanceof PsiReferenceExpression && ((PsiReferenceExpression)ref).resolve() == def) {
      final PsiElement defContainer = LambdaUtil.getContainingClassOrLambda(def);
      PsiElement refContainer = LambdaUtil.getContainingClassOrLambda(ref);
      while (defContainer != refContainer && refContainer != null) {
        ref = refContainer;
        refContainer = LambdaUtil.getContainingClassOrLambda(refContainer.getParent());
      }
    }

    try {
      RefsDefs refsDefs = new RefsDefs(body) {
        final PsiManager psiManager = def.getManager();
        private final IntList[] myBackwardTraces = getBackwardTraces(instructions);

        @Override
        protected int nNext(int index) {
          return myBackwardTraces[index].size();
        }

        @Override
        protected int getNext(int index, int no) {
          return myBackwardTraces[index].getInt(no);
        }

        @Override
        protected boolean defs() {
          return true;
        }

        @Override
        protected void processInstruction(final @NotNull Set<? super PsiElement> res, final @NotNull Instruction instruction, int index) {
          if (instruction instanceof WriteVariableInstruction) {
            WriteVariableInstruction instructionW = (WriteVariableInstruction)instruction;
            if (psiManager.areElementsEquivalent(instructionW.variable, def)) {
              final PsiElement element = flow.getElement(index);
              element.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitReferenceExpression(@NotNull PsiReferenceExpression ref) {
                  if (PsiUtil.isAccessedForWriting(ref) && psiManager.areElementsEquivalent(ref.resolve(), def)) {
                    res.add(ref);
                  }
                }

                @Override
                public void visitVariable(@NotNull PsiVariable var) {
                  if ((var instanceof PsiParameter || var.hasInitializer()) && psiManager.areElementsEquivalent(var, def)) {
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
      if (rethrow) {
        ExceptionUtil.rethrowAllAsUnchecked(e);
      }
      return PsiElement.EMPTY_ARRAY;
    }
  }

  /// @see #getRefs(PsiCodeBlock, PsiVariable, PsiElement, boolean)
  public static PsiElement @NotNull [] getRefs(@NotNull PsiCodeBlock body, @NotNull PsiVariable def, @NotNull PsiElement ref) {
    return getRefs(body, def, ref, false);
  }

  /// Returns references to variable `def` which have the value of `ref` in the scope of `block`.
  /// `ref` is the value assigned to the variable `def`.
  ///
  /// ### Example
  ///
  /// Consider the following code:
  ///
  /// ```java
  /// void testHighlightBecauseIncorrect() {
  ///   String name = "foo ".trim();
  ///   println(name);
  ///   println(name);
  ///   name = "bar";
  ///   println(name);
  /// }
  /// ```
  ///
  /// Here's an example of arguments that you could call this method with (in the context of the sample code above):
  ///  * `block` is the method's body
  ///  * `def` is [PsiLocalVariable]: `String name = "foo ".trim()`
  ///  * `ref` is [PsiMethodCallExpression]: `"foo ".trim()`
  ///
  /// In the code above there are 3 [PsiReferenceExpression]s of `name`, but this method would only return the first two.
  /// The third [PsiReferenceExpression] of `name` would not be included, because `name` was redefined by that point and no longer
  /// has the value of `def` (which at this point is not `"foo ".trim()`, but `"bar"`).
  ///
  /// ### See also:
  ///  * `DeclarationSearchUtils.findDefinition` to find the definition of the [PsiReferenceExpression]
  ///  * {@link PsiTreeUtil#getParentOfType(PsiElement, Class)} to find the block containing the code you want to analyze
  public static PsiElement[] getRefs(@NotNull PsiCodeBlock body, @NotNull PsiVariable def, @NotNull PsiElement ref, boolean rethrow) {
    try {
      RefsDefs refsDefs = new RefsDefs(body) {
        final PsiManager psiManager = def.getManager();
        @Override
        protected int nNext(int index) {
          return instructions.get(index).nNext();
        }

        @Override
        protected int getNext(int index, int no) {
          return instructions.get(index).getNext(index, no);
        }

        @Override
        protected boolean defs() {
          return false;
        }

        @Override
        protected void processInstruction(final @NotNull Set<? super PsiElement> res, final @NotNull Instruction instruction, int index) {
          if (instruction instanceof ReadVariableInstruction) {
            ReadVariableInstruction instructionR = (ReadVariableInstruction)instruction;
            if (psiManager.areElementsEquivalent(instructionR.variable, def)) {

              final PsiElement element = flow.getElement(index);
              element.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitReferenceExpression(@NotNull PsiReferenceExpression ref) {
                  if (ref.isReferenceTo(def)) {
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
      if (rethrow) {
        ExceptionUtil.rethrowAllAsUnchecked(e);
      }
      return PsiElement.EMPTY_ARRAY;
    }
  }

  private abstract static class RefsDefs {
    protected abstract int   nNext(int index);
    protected abstract int getNext(int index, int no);

    final @NotNull List<Instruction> instructions;
    final ControlFlow flow;
    final PsiCodeBlock body;


    RefsDefs(@NotNull PsiCodeBlock body) throws AnalysisCanceledException {
      this.body = body;
      flow = ControlFlowFactory.getControlFlow(body, ourPolicy, ControlFlowOptions.NO_CONST_EVALUATE);
      instructions = flow.getInstructions();
    }

    protected abstract void processInstruction(@NotNull Set<? super PsiElement> res, @NotNull Instruction instruction, int index);
    protected abstract boolean defs ();

    private PsiElement @NotNull [] get(@NotNull PsiVariable def, @NotNull PsiElement refOrDef) {
      if (body == null) {
        return PsiElement.EMPTY_ARRAY;
      }

      final boolean [] visited = new boolean[instructions.size() + 1];
      visited [visited.length-1] = true; // stop on the code end
      final boolean defs = defs();
      int elem = defs ? flow.getStartOffset(refOrDef) : flow.getEndOffset(refOrDef);

      // hack: ControlFlow doesn't contains parameters initialization
      if (elem == -1 && def instanceof PsiParameter) {
        elem = 0;
      }

      if (elem != -1) {
        if (!defs && instructions.get(elem) instanceof ReadVariableInstruction) {
          LOG.assertTrue(nNext(elem) == 1);
          LOG.assertTrue(getNext(elem,0) == elem+1);
          elem += 1;
        }

        Set<@NotNull PsiElement> res = new HashSet<>();
        // hack: ControlFlow doesn't contains parameters initialization
        int startIndex = elem;

        IntList workQueue = new IntArrayList();
        workQueue.add(startIndex);
        PsiManager psiManager = body.getManager();

        while (!workQueue.isEmpty()) {
          int index = workQueue.removeInt(workQueue.size() - 1);
          if (visited[index]) {
            continue;
          }
          visited [index] = true;

          if (defs) {
            final Instruction instruction = instructions.get(index);
            processInstruction(res, instruction, index);
            if (instruction instanceof WriteVariableInstruction) {
              WriteVariableInstruction instructionW = (WriteVariableInstruction)instruction;
              if (psiManager.areElementsEquivalent(instructionW.variable, def)) {
                continue;
              }
            }

            // hack: ControlFlow doesn't contains parameters initialization
            if (index == 0 && def instanceof PsiParameter) {
              PsiIdentifier identifier = def.getNameIdentifier();
              if (identifier != null) {
                res.add(identifier);
              }
            }
          }

          final int nNext = nNext (index);
          for (int i = 0; i < nNext; i++) {
            final int prev = getNext(index, i);
            if (!visited [prev]) {
              if (!defs) {
                final Instruction instruction = instructions.get(prev);
                if (instruction instanceof WriteVariableInstruction) {
                  WriteVariableInstruction instructionW = (WriteVariableInstruction)instruction;
                  if (psiManager.areElementsEquivalent(instructionW.variable, def)) {
                    continue;
                  }
                }
                else {
                  processInstruction(res, instruction, prev);
                }
              }
              workQueue.add(prev);
            }
          }
        }
        return PsiUtilCore.toPsiElementArray(res);
      }
      return PsiElement.EMPTY_ARRAY;
    }
  }


  private static IntList @NotNull [] getBackwardTraces(@NotNull List<? extends Instruction> instructions) {
    final IntList[] states = new IntList[instructions.size()];
    for (int i = 0; i < states.length; i++) {
      states[i] = new IntArrayList();
    }

    for (int i = 0; i < instructions.size(); i++) {
      final Instruction instruction = instructions.get(i);
      for (int j = 0; j != instruction.nNext(); ++ j) {
        final int next = instruction.getNext(i, j);
        if (next < states.length) {
          states[next].add(i);
        }
      }
    }
    return states;
  }

  private static class WalkThroughStack {
    private final Stack<InstructionKey> myFrom;
    private final Stack<InstructionKey> myNext;

    WalkThroughStack(int size) {
      if (size < 2) size = 2;
      myFrom = new Stack<>(size);
      myNext = new Stack<>(size);
    }

    void push(@NotNull InstructionKey fromKey, @NotNull InstructionKey nextKey) {
      myFrom.push(fromKey);
      myNext.push(nextKey);
    }

    @NotNull
    InstructionKey peekFrom() {
      return myFrom.peek();
    }

    @NotNull
    InstructionKey popNext() {
      myFrom.pop();
      return myNext.pop();
    }

    boolean isEmpty() {
      return myFrom.isEmpty();
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0, limit = Math.min(myFrom.size(), myNext.size()); i < limit; i++) {
        if (sb.length() != 0) sb.append(", ");
        sb.append(myFrom.get(i)).append("->").append(myNext.get(i));
      }
      return sb.toString();
    }
  }

  private static final class InstructionStateWalker {
    private final Map<InstructionKey, InstructionState> myStates;
    private final WalkThroughStack myWalkThroughStack;
    private final List<? extends Instruction> myInstructions;

    private InstructionStateWalker(@NotNull List<? extends Instruction> instructions) {
      myStates = new HashMap<>(instructions.size());
      myWalkThroughStack = new WalkThroughStack(instructions.size() / 2);
      myInstructions = instructions;
    }

    private @NotNull Map<InstructionKey, InstructionState> walk() {
      InstructionKey startKey = InstructionKey.create(0);
      myStates.put(startKey, new InstructionState(startKey));
      myWalkThroughStack.push(InstructionKey.create(-1), startKey);

      InstructionKeySet visited = new InstructionKeySet(myInstructions.size() + 1);
      while (!myWalkThroughStack.isEmpty()) {
        ProgressManager.checkCanceled();
        InstructionKey fromKey = myWalkThroughStack.peekFrom();
        InstructionKey nextKey = myWalkThroughStack.popNext();
        addBackwardTrace(fromKey, nextKey);
        if (!visited.contains(nextKey)) {
          visit(nextKey);
          visited.add(nextKey);
        }
      }
      return myStates;
    }

    private void visit(@NotNull InstructionKey fromKey) {
      if (fromKey.getOffset() >= myInstructions.size()) return;
      final Instruction instruction = myInstructions.get(fromKey.getOffset());
      if (instruction instanceof CallInstruction) {
        int nextOffset = ((CallInstruction)instruction).offset;
        LOG.assertTrue(nextOffset != 0);
        int returnOffset = fromKey.getOffset() + 1;
        InstructionKey nextKey = fromKey.push(nextOffset, returnOffset);
        myWalkThroughStack.push(fromKey, nextKey);
      }
      else if (instruction instanceof ReturnInstruction) {
        int overriddenOffset = ((ReturnInstruction)instruction).offset;
        InstructionKey nextKey = fromKey.pop(overriddenOffset);
        myWalkThroughStack.push(fromKey, nextKey);
      }
      else {
        for (int no = 0; no != instruction.nNext(); no++) {
          final int nextOffset = instruction.getNext(fromKey.getOffset(), no);
          InstructionKey nextKey = fromKey.next(nextOffset);
          myWalkThroughStack.push(fromKey, nextKey);
        }
      }
    }

    private void addBackwardTrace(@NotNull InstructionKey fromKey, @NotNull InstructionKey nextKey) {
      if (fromKey.getOffset() >= 0 && nextKey.getOffset() < myInstructions.size()) {
        InstructionState state = myStates.get(nextKey);
        if (state == null) myStates.put(nextKey, state = new InstructionState(nextKey));
        state.addBackwardTrace(fromKey);
      }
    }

    static @NotNull Map<InstructionKey, InstructionState> getStates(@NotNull List<? extends Instruction> instructions) {
      return new InstructionStateWalker(instructions).walk();
    }
  }

  private static final ControlFlowPolicy ourPolicy = new ControlFlowPolicy() {
    @Override
    public PsiVariable getUsedVariable(@NotNull PsiReferenceExpression refExpr) {
      if (refExpr.isQualified()) return null;

      PsiElement refElement = refExpr.resolve();
      if (refElement instanceof PsiLocalVariable || refElement instanceof PsiParameter) {
        return (PsiVariable) refElement;
      }

      return null;
    }

    @Override
    public boolean isParameterAccepted(@NotNull PsiParameter psiParameter) {
      return true;
    }

    @Override
    public boolean isLocalVariableAccepted(@NotNull PsiLocalVariable psiVariable) {
      return true;
    }
  };
}
