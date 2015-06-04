/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
 * Date: Jan 28, 2002
 * Time: 10:16:39 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.BranchingInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.java.DfaValueFactoryJava;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DataFlowRunner extends AbstractDataFlowRunner {
  
  private final MultiMap<PsiElement, DfaMemoryState> myNestedClosures = new MultiMap<PsiElement, DfaMemoryState>();
  private final DfaValueFactoryJava myValueFactory;
  private final boolean myIgnoreAssertions;

  protected DataFlowRunner() {
    this(false, true, false);
  }

  protected DataFlowRunner(boolean unknownMembersAreNullable, boolean honorFieldInitializers, boolean ignoreAssertions) {
    myValueFactory = new DfaValueFactoryJava(honorFieldInitializers, unknownMembersAreNullable);
    myIgnoreAssertions = ignoreAssertions;
  }

  @NotNull
  @Override
  public DfaValueFactoryJava getFactory() {
    return myValueFactory;
  }

  @Override
  protected void prepareAnalysis(@NotNull PsiElement psiBlock, Iterable<DfaMemoryState> initialStates) {
    super.prepareAnalysis(psiBlock, initialStates);
    myNestedClosures.clear();
  }

  @Nullable
  @Override
  protected Collection<DfaMemoryState> createInitialStates(@NotNull PsiElement psiBlock, @NotNull InstructionVisitor visitor) {
    PsiClass containingClass = PsiTreeUtil.getParentOfType(psiBlock, PsiClass.class);
    if (containingClass != null && PsiUtil.isLocalOrAnonymousClass(containingClass)) {
      final PsiElement parent = containingClass.getParent();
      final PsiCodeBlock block = DfaPsiUtil.getTopmostBlockInSameClass(parent);
      if ((parent instanceof PsiNewExpression || parent instanceof PsiDeclarationStatement) && block != null) {
        final RunnerResult result = analyzeMethod(block, visitor);
        if (result == RunnerResult.OK) {
          final Collection<DfaMemoryState> closureStates = myNestedClosures.get(DfaPsiUtil.getTopmostBlockInSameClass(psiBlock));
          if (!closureStates.isEmpty()) {
            return closureStates;
          }
        }
        return null;
      }
    }

    return Collections.singletonList(createMemoryState());
  }

  @NotNull
  @Override
  protected DfaInstructionState[] acceptInstruction(InstructionVisitor visitor, DfaInstructionState instructionState) {
    Instruction instruction = instructionState.getInstruction();
    PsiElement closure = DfaUtil.getClosureInside(instruction);
    if (closure instanceof PsiClass) {
      registerNestedClosures(instructionState, (PsiClass)closure);
    } else if (closure instanceof PsiLambdaExpression) {
      registerNestedClosures(instructionState, (PsiLambdaExpression)closure);
    }

    return instruction.accept(instructionState.getMemoryState(), visitor);
  }

  private void registerNestedClosures(DfaInstructionState instructionState, PsiClass nestedClass) {
    DfaMemoryState state = instructionState.getMemoryState();
    for (PsiMethod method : nestedClass.getMethods()) {
      PsiCodeBlock body = method.getBody();
      if (body != null) {
        myNestedClosures.putValue(body, createClosureState(state));
      }
    }
    for (PsiClassInitializer initializer : nestedClass.getInitializers()) {
      myNestedClosures.putValue(initializer.getBody(), createClosureState(state));
    }
    for (PsiField field : nestedClass.getFields()) {
      myNestedClosures.putValue(field, createClosureState(state));
    }
  }
  
  private void registerNestedClosures(DfaInstructionState instructionState, PsiLambdaExpression expr) {
    DfaMemoryState state = instructionState.getMemoryState();
    PsiElement body = expr.getBody();
    if (body != null) {
      myNestedClosures.putValue(body, createClosureState(state));
    }
  }

  @NotNull
  @Override
  protected IControlFlowAnalyzer createControlFlowAnalyzer(@NotNull PsiElement block) {
    return new ControlFlowAnalyzer(myValueFactory, myIgnoreAssertions, block);
  }

  @NotNull
  @Override
  protected DfaMemoryState createMemoryState() {
    return new DfaMemoryStateImpl(myValueFactory);
  }

  public MultiMap<PsiElement, DfaMemoryState> getNestedClosures() {
    return new MultiMap<PsiElement, DfaMemoryState>(myNestedClosures);
  }

  private static DfaMemoryStateImpl createClosureState(DfaMemoryState memState) {
    DfaMemoryStateImpl copy = (DfaMemoryStateImpl)memState.createCopy();
    copy.flushFields();
    Set<DfaVariableValue> vars = new HashSet<DfaVariableValue>(copy.getVariableStates().keySet());
    for (DfaVariableValue value : vars) {
      copy.flushDependencies(value);
    }
    copy.emptyStack();
    return copy;
  }
}
