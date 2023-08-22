// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashSetInterner;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.containers.Interner;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class SuppressableInspectionTreeNode extends InspectionTreeNode {
  @NotNull
  private final InspectionToolPresentation myPresentation;
  private volatile Set<SuppressIntentionAction> myAvailableSuppressActions;
  private volatile @Nls String myPresentableName;
  private volatile Boolean myValid;
  private volatile NodeState myPreviousState;

  SuppressableInspectionTreeNode(@NotNull InspectionToolPresentation presentation, @NotNull InspectionTreeNode parent) {
    super(parent);
    myPresentation = presentation;
  }

  void nodeAdded() {
    super.dropProblemCountCaches();
    ReadAction.run(() -> myValid = calculateIsValid());
    //force calculation
    getProblemLevels();
  }

  @Override
  protected boolean doesNeedInternProblemLevels() {
    return true;
  }

  @NotNull
  public InspectionToolPresentation getPresentation() {
    return myPresentation;
  }

  public boolean canSuppress() {
    return getChildren().isEmpty();
  }

  public abstract boolean isAlreadySuppressedFromView();

  public abstract boolean isQuickFixAppliedFromView();

  @Override
  void dropProblemCountCaches() {
    super.dropProblemCountCaches();
    NodeState currentState = calculateState();
    if (!currentState.equals(myPreviousState)) {
      myPreviousState = currentState;
    }
  }

  @NotNull
  public synchronized Set<SuppressIntentionAction> getAvailableSuppressActions() {
    if (myAvailableSuppressActions == null) {
      updateAvailableSuppressActions();
    }
    return myAvailableSuppressActions;
  }

  public void updateAvailableSuppressActions() {
    myAvailableSuppressActions = calculateAvailableSuppressActions();
  }

  public void removeSuppressActionFromAvailable(@NotNull SuppressIntentionAction action) {
    myAvailableSuppressActions.remove(action);
  }

  @Nullable
  public abstract RefEntity getElement();

  @Override
  public final synchronized boolean isValid() {
    Boolean valid = myValid;
    if (valid == null) {
      valid = ReadAction.compute(() -> calculateIsValid());
      myValid = valid;
    }
    return valid;
  }

  @Override
  public final synchronized String getPresentableText() {
    String name = myPresentableName;
    if (name == null) {
      name = calculatePresentableName();
      myPresentableName = name;
    }
    return name;
  }

  @Nullable
  @Override
  public String getTailText() {
    if (isQuickFixAppliedFromView()) {
      return "";
    }
    if (isAlreadySuppressedFromView()) {
      return LangBundle.message("suppressed");
    }
    return !isValid() ? LangBundle.message("no.longer.valid") : null;
  }

  @NotNull
  private Set<SuppressIntentionAction> calculateAvailableSuppressActions() {
    return getElement() == null
                                 ? Collections.emptySet()
                                 : calculateAvailableSuppressActions(myPresentation.getContext().getProject());
  }

  @NotNull
  public abstract Pair<PsiElement, CommonProblemDescriptor> getSuppressContent();

  @NotNull
  private Set<SuppressIntentionAction> calculateAvailableSuppressActions(@NotNull Project project) {
    if (myPresentation.isDummy()) return Collections.emptySet();
    final Pair<PsiElement, CommonProblemDescriptor> suppressContent = getSuppressContent();
    PsiElement element = suppressContent.getFirst();
    if (element == null) return Collections.emptySet();
    InspectionResultsView view = myPresentation.getContext().getView();
    if (view == null) return Collections.emptySet();
    InspectionViewSuppressActionHolder suppressActionHolder = view.getSuppressActionHolder();
    final SuppressIntentionAction[] actions = suppressActionHolder.getSuppressActions(myPresentation.getToolWrapper(), element);
    if (actions.length == 0) return Collections.emptySet();
    return suppressActionHolder.internSuppressActions(Arrays.stream(actions)
      .filter(action -> action.isAvailable(project, null, element))
      .collect(Collectors.toCollection(() -> ConcurrentCollectionFactory.createConcurrentSet(HashingStrategy.identity()))));
  }

  @Nls
  protected abstract String calculatePresentableName();

  protected abstract boolean calculateIsValid();

  protected void dropCaches() {
    doDropCache();
    dropProblemCountCaches();
  }

  private void doDropCache() {
    myProblemLevels.drop();
    if (isQuickFixAppliedFromView() || isAlreadySuppressedFromView()) return;
    // calculate all data on background thread
    ReadAction.run(() -> {
      myValid = calculateIsValid();
      myPresentableName = calculatePresentableName();
    });

    for (InspectionTreeNode child : getChildren()) {
      if (child instanceof SuppressableInspectionTreeNode) {
        ((SuppressableInspectionTreeNode)child).doDropCache();
      }
    }
  }

  private record NodeState(boolean isValid, boolean isSuppressed, boolean isFixApplied, boolean isExcluded) {
    private static final Interner<NodeState> INTERNER = new HashSetInterner<>();
  }

  private NodeState calculateState() {
    NodeState state = new NodeState(isValid(), isAlreadySuppressedFromView(), isQuickFixAppliedFromView(), isExcluded());
    synchronized (NodeState.INTERNER) {
      return NodeState.INTERNER.intern(state);
    }
  }
}
