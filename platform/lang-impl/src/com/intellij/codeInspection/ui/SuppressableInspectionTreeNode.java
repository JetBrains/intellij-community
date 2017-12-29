/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class SuppressableInspectionTreeNode extends InspectionTreeNode {
  @NotNull
  private final InspectionToolPresentation myPresentation;
  private volatile Set<SuppressIntentionAction> myAvailableSuppressActions;
  private volatile String myPresentableName;
  private volatile Boolean myValid;
  private volatile NodeState myPreviousState;

  protected SuppressableInspectionTreeNode(Object userObject, @NotNull InspectionToolPresentation presentation) {
    super(userObject);
    myPresentation = presentation;
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
    return isLeaf();
  }

  public abstract boolean isAlreadySuppressedFromView();

  public abstract boolean isQuickFixAppliedFromView();

  @Override
  protected boolean isProblemCountCacheValid() {
    NodeState currentState = calculateState();
    if (myPreviousState == null || !currentState.equals(myPreviousState)) {
      myPreviousState = currentState;
      return false;
    }
    return true;
  }

  @Override
  public int getProblemCount(boolean allowSuppressed) {
    return !isExcluded() && isValid() && !isQuickFixAppliedFromView() && (allowSuppressed || !isAlreadySuppressedFromView()) ? 1 : 0;
  }

  @NotNull
  public synchronized Set<SuppressIntentionAction> getAvailableSuppressActions() {
    Set<SuppressIntentionAction> actions = myAvailableSuppressActions;
    if (actions == null) {
      actions = calculateAvailableSuppressActions();
      myAvailableSuppressActions = actions;
    }
    return actions;
  }

  public void removeSuppressActionFromAvailable(@NotNull SuppressIntentionAction action) {
    myAvailableSuppressActions.remove(action);
  }

  @Nullable
  public abstract RefEntity getElement();

  @Nullable
  public abstract CommonProblemDescriptor getDescriptor();

  @Override
  public final synchronized boolean isValid() {
    Boolean valid = myValid;
    if (valid == null) {
      valid = calculateIsValid();
      myValid = valid;
    }
    return valid;
  }

  @Override
  public final synchronized String toString() {
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
      return "Suppressed";
    }
    return !isValid() ? "No longer valid" : null;
  }

  @Override
  protected void nodeAddedToTree() {
    myPresentableName = calculatePresentableName();
    myValid = calculateIsValid();
    myAvailableSuppressActions = calculateAvailableSuppressActions();
  }

  @NotNull
  private Set<SuppressIntentionAction> calculateAvailableSuppressActions() {
    return getElement() == null
                                 ? Collections.emptySet()
                                 : calculateAvailableSuppressActions(myPresentation.getContext().getProject());
  }

  @NotNull
  public final Pair<PsiElement, CommonProblemDescriptor> getSuppressContent() {
    RefEntity refElement = getElement();
    CommonProblemDescriptor descriptor = getDescriptor();
    PsiElement element = descriptor instanceof ProblemDescriptor
                         ? ((ProblemDescriptor)descriptor).getPsiElement()
                         : refElement instanceof RefElement
                           ? ((RefElement)refElement).getElement()
                           : null;
    return Pair.create(element, descriptor);
  }

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
      .collect(Collectors.toCollection(() -> ConcurrentCollectionFactory.createConcurrentSet(ContainerUtil.identityStrategy()))));
  }

  protected abstract String calculatePresentableName();

  protected abstract boolean calculateIsValid();

  protected void dropCache() {
    myProblemLevels.drop();
    if (isQuickFixAppliedFromView() || isAlreadySuppressedFromView()) return;
    myValid = calculateIsValid();
    myPresentableName = calculatePresentableName();
    for (int i = 0; i < getChildCount(); i++) {
      TreeNode child = getChildAt(i);
      if (child instanceof SuppressableInspectionTreeNode) {
        ((SuppressableInspectionTreeNode)child).dropCache();
      }
    }
  }

  private static class NodeState {
    private static final Interner<NodeState> INTERNER = new Interner<>();
    private final boolean isValid;
    private final boolean isSuppressed;
    private final boolean isFixApplied;

    private NodeState(boolean isValid, boolean isSuppressed, boolean isFixApplied) {
      this.isValid = isValid;
      this.isSuppressed = isSuppressed;
      this.isFixApplied = isFixApplied;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      NodeState state = (NodeState)o;

      if (isValid != state.isValid) return false;
      if (isSuppressed != state.isSuppressed) return false;
      if (isFixApplied != state.isFixApplied) return false;

      return true;
    }

    @Override
    public int hashCode() {
     return (isValid ? 0x1 : 0) + (isFixApplied ? 0x2 : 0) + (isSuppressed ? 0x4 : 0);
    }
  }

  protected NodeState calculateState() {
    return NodeState.INTERNER.intern(new NodeState(isValid(), isAlreadySuppressedFromView(), isQuickFixAppliedFromView()));
  }
}
