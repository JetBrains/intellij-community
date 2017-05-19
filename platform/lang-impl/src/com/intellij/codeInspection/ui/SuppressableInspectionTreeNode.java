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
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.util.Collections;
import java.util.Set;

public abstract class SuppressableInspectionTreeNode extends InspectionTreeNode {
  @NotNull
  private final InspectionResultsView myView;
  @NotNull
  private final InspectionToolPresentation myPresentation;
  private volatile Set<SuppressIntentionAction> myAvailableSuppressActions;
  private volatile String myPresentableName;
  private volatile Boolean myValid;

  protected SuppressableInspectionTreeNode(Object userObject, @NotNull InspectionToolPresentation presentation) {
    super(userObject);
    myView = presentation.getContext().getView();
    myPresentation = presentation;
  }

  @NotNull
  public InspectionToolPresentation getPresentation() {
    return myPresentation;
  }

  public boolean canSuppress() {
    return isLeaf();
  }

  public final boolean isAlreadySuppressedFromView() {
    final Object usrObj = getUserObject();
    return usrObj != null && myView.getSuppressedNodes(myPresentation.getToolWrapper().getShortName()).contains(usrObj);
  }

  public abstract boolean isQuickFixAppliedFromView();

  @NotNull
  public Set<SuppressIntentionAction> getAvailableSuppressActions() {
    return myAvailableSuppressActions;
  }

  public void removeSuppressActionFromAvailable(@NotNull SuppressIntentionAction action) {
    myAvailableSuppressActions.remove(action);
  }

  protected void init(Project project) {
    myPresentableName = calculatePresentableName();
    myValid = calculateIsValid();
    myAvailableSuppressActions = getElement() == null
                                 ? Collections.emptySet()
                                 : getOnlyAvailableSuppressActions(project);
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
    if (!isValid()) {
      return "No longer valid";
    }
    return isAlreadySuppressedFromView() ? "Suppressed" : null;
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
  private Set<SuppressIntentionAction> getOnlyAvailableSuppressActions(@NotNull Project project) {
    final Set<SuppressIntentionAction> actions = myView.getSuppressActions(myPresentation.getToolWrapper());
    if (actions.isEmpty()) {
      return Collections.emptySet();
    }
    final Pair<PsiElement, CommonProblemDescriptor> suppress = getSuppressContent();
    final PsiElement suppressElement = suppress.getFirst();
    if (suppressElement == null) {
      return actions;
    }
    Set<SuppressIntentionAction> availableActions = null;
    for (SuppressIntentionAction action : actions) {
      if (action.isAvailable(project, null, suppressElement)) {
        if (availableActions == null) {
          availableActions = ContainerUtil.newConcurrentSet(TObjectHashingStrategy.IDENTITY);
        }
        availableActions.add(action);
      }
    }

    return availableActions == null ? Collections.emptySet() : availableActions;
  }

  protected abstract String calculatePresentableName();

  protected abstract boolean calculateIsValid();

  protected void dropCache(Project project) {
    myValid = calculateIsValid();
    myPresentableName = calculatePresentableName();
    for (int i = 0; i < getChildCount(); i++) {
      TreeNode child = getChildAt(i);
      if (child instanceof SuppressableInspectionTreeNode) {
        ((SuppressableInspectionTreeNode)child).dropCache(project);
      }
    }
  }
}
