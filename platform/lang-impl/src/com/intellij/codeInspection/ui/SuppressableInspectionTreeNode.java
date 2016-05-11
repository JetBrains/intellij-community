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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public abstract class SuppressableInspectionTreeNode extends CachedInspectionTreeNode implements RefElementAndDescriptorAware {
  private final static Logger LOG = Logger.getInstance(SuppressableInspectionTreeNode.class);
  @NotNull
  private final InspectionResultsView myView;
  private volatile Set<SuppressIntentionAction> myAvailableSuppressActions;
  protected final InspectionToolPresentation myPresentation;

  protected SuppressableInspectionTreeNode(Object userObject, @NotNull InspectionToolPresentation presentation) {
    super(userObject);
    myView = presentation.getContext().getView();
    myPresentation = presentation;
  }

  public boolean canSuppress() {
    return isLeaf();
  }

  public final boolean isAlreadySuppressedFromView() {
    final Object usrObj = getUserObject();
    LOG.assertTrue(usrObj != null);
    return myView.getSuppressedNodes().contains(usrObj);
  }

  public final void markAsSuppressedFromView() {
    final Object usrObj = getUserObject();
    LOG.assertTrue(usrObj != null);
    myView.getSuppressedNodes().add(usrObj);
  }

  @Nullable
  @Override
  public String getCustomizedTailText() {
    final String text = super.getCustomizedTailText();
    if (text != null) {
      return text;
    }
    return isAlreadySuppressedFromView() ? "Suppressed" : null;
  }

  @NotNull
  public Set<SuppressIntentionAction> getAvailableSuppressActions() {
    Set<SuppressIntentionAction> actions = myAvailableSuppressActions;
    if (actions == null) {
      synchronized (this) {
        if ((actions = myAvailableSuppressActions) == null) {
          final RefEntity element = getElement();
          if (element == null) {
            actions = getSuppressActions();
          }
          else {
            actions = getOnlyAvailableSuppressActions(element.getRefManager().getProject());
          }
          myAvailableSuppressActions = actions;
        }
      }
    }
    return actions;
  }

  @Override
  protected void init(Project project) {
    super.init(project);
    myAvailableSuppressActions = getOnlyAvailableSuppressActions(project);
  }

  @Override
  protected void dropCache(Project project) {
    super.dropCache(project);
    if (isValid()) {
      synchronized (this) {
        myAvailableSuppressActions = getOnlyAvailableSuppressActions(project);
      }
    }
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
    final Set<SuppressIntentionAction> actions = getSuppressActions();
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
          availableActions = new THashSet<>(actions.size());
        }
        availableActions.add(action);
      }
    }

    return availableActions == null ? Collections.emptySet() : availableActions;
  }

  @NotNull
  private Set<SuppressIntentionAction> getSuppressActions() {
    return myView.getSuppressActions(myPresentation.getToolWrapper());
  }
}
