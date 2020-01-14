// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl;

import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.refactoring.extractMethod.newImpl.structures.CodeFragment;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.controlFlow.ControlFlowUtil.DEFAULT_EXIT_STATEMENTS_CLASSES;

public class ControlFlowOnFragment {
  private final CodeFragment fragment;
  private final ControlFlow flow;
  private final int flowStart;
  private final int flowEnd;

  private ControlFlowOnFragment(CodeFragment fragment, ControlFlow flow, int flowStart, int flowEnd) {
    this.fragment = fragment;
    this.flow = flow;
    this.flowStart = flowStart;
    this.flowEnd = flowEnd;
  }

  private static ControlFlow createControlFlow(CodeFragment fragment) throws AnalysisCanceledException {
    PsiElement fragmentToAnalyze = ControlFlowUtil.findCodeFragment(fragment.getFirstElement());
    final LocalsControlFlowPolicy flowPolicy = new LocalsControlFlowPolicy(fragmentToAnalyze);
    final ControlFlowFactory factory = ControlFlowFactory.getInstance(fragment.getProject());
    return factory.getControlFlow(fragmentToAnalyze, flowPolicy, false, false);
  }

  private static int findStartInFlow(ControlFlow flow, CodeFragment fragment) {
    return flow.getStartOffset(fragment.getFirstElement());
  }

  private static int findEndInFlow(ControlFlow flow, CodeFragment fragment) {
    return flow.getEndOffset(fragment.getLastElement());
  }

  public static ControlFlowOnFragment create(CodeFragment fragment) {
    try {
      final ControlFlow flow = createControlFlow(fragment);
      final int flowStart = findStartInFlow(flow, fragment);
      final int flowEnd = findEndInFlow(flow, fragment);
      return new ControlFlowOnFragment(fragment, flow, flowStart, flowEnd);
    }
    catch (AnalysisCanceledException e) {
      throw new IllegalArgumentException("Code fragment may have syntax error");
    }
  }

  public List<PsiVariable> findInputVariables() {
    return ControlFlowUtil.getInputVariables(flow, flowStart, flowEnd);
  }

  public List<PsiVariable> findOutputVariables() {
    final IntArrayList exitPoints = new IntArrayList();
    ControlFlowUtil.findExitPointsAndStatements(flow, flowStart, flowEnd, exitPoints, DEFAULT_EXIT_STATEMENTS_CLASSES);
    final PsiVariable[] outputVariables = ControlFlowUtil.getOutputVariables(flow, flowStart, flowEnd, exitPoints.toArray());
    final Set<PsiVariable> distinctVariables = ContainerUtil.set(outputVariables);
    return new ArrayList<>(distinctVariables);
  }

  public boolean canCompleteNormally() {
    return ControlFlowUtil.canCompleteNormally(flow, flowStart, flowEnd);
  }

  public List<PsiStatement> findExitStatements() {
    final IntArrayList exitPoints = new IntArrayList();
    final Collection<PsiStatement> statements =
      ControlFlowUtil.findExitPointsAndStatements(flow, flowStart, flowEnd, exitPoints, DEFAULT_EXIT_STATEMENTS_CLASSES);
    return ContainerUtil.filter(statements, statement -> ! isExitInside(statement));
  }

  public boolean hasSingleExit() {
    final IntArrayList exitPoints = new IntArrayList();
    final Collection<PsiStatement> statements =
      ControlFlowUtil.findExitPointsAndStatements(flow, flowStart, flowEnd, exitPoints, DEFAULT_EXIT_STATEMENTS_CLASSES);
    return exitPoints.size() == 1;
  }

  public List<PsiVariable> findWrittenVariables() {
    return new ArrayList<>(ControlFlowUtil.getWrittenVariables(flow, flowStart, flowEnd, false));
  }

  private boolean isExitInside(PsiStatement statement){
    if (statement instanceof PsiBreakStatement) {
      return contains(((PsiBreakStatement)statement).findExitedStatement());
    }
    else if (statement instanceof PsiContinueStatement){
      return contains(((PsiContinueStatement)statement).findContinuedStatement());
    }
    else if (statement instanceof PsiReturnStatement) {
      return false;
    }
    else {
      throw new IllegalArgumentException();
    }
  }

  private boolean contains(@Nullable PsiStatement statement){
    if (statement == null) return false;
    int startOffset = flow.getStartOffset(statement);
    int endOffset = flow.getEndOffset(statement);
    if (flowStart <= startOffset && endOffset <= flowEnd) {
      return true;
    } else {
      return false;
    }
  }
}