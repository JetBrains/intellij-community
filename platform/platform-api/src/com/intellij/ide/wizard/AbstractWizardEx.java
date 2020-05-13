// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBCardLayout;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AbstractWizardEx extends AbstractWizard<AbstractWizardStepEx> {
  private final String myTitle;
  private final Map<Object, Integer> myStepId2Index = new HashMap<>();
  private final Int2ObjectOpenHashMap<AbstractWizardStepEx> myIndex2Step = new Int2ObjectOpenHashMap<>();

  public AbstractWizardEx(String title, @Nullable Project project, List<? extends AbstractWizardStepEx> steps) {
    super(title, project);
    myTitle = title;

    int index = 0;
    for (AbstractWizardStepEx step : steps) {
      myStepId2Index.put(step.getStepId(), index);
      myIndex2Step.put(index, step);
      addStep(step);

      step.addStepListener(new AbstractWizardStepEx.Listener() {
        @Override
        public void stateChanged() {
          updateButtons();
        }

        @Override
        public void doNextAction() {
          if (getNextButton().isEnabled()) {
            AbstractWizardEx.this.doNextAction();
          }
        }
      });
      index++;
    }

    init();
  }

  @Override
  protected void doPreviousAction() {
    // Commit data of current step
    final AbstractWizardStepEx currentStep = mySteps.get(myCurrentStep);
    try {
      currentStep._commitPrev();
    }
    catch (final CommitStepCancelledException e) {
      return;
    }
    catch (CommitStepException e) {
      Messages.showErrorDialog(getContentPane(), e.getMessage());
      return;
    }

    myCurrentStep = getPreviousStep(myCurrentStep);
    updateStep(JBCardLayout.SwipeDirection.BACKWARD);
  }

  @Override
  protected void doNextAction() {
    // Commit data of current step
    final AbstractWizardStepEx currentStep = mySteps.get(myCurrentStep);
    try {
      currentStep._commit(false);
    }
    catch (final CommitStepCancelledException e) {
      return;
    }
    catch (CommitStepException e) {
      Messages.showErrorDialog(getContentPane(), e.getMessage());
      return;
    }

    if (isLastStep()) {
      doOKAction();
      return;
    }
    myCurrentStep = getNextStep(myCurrentStep);
    updateStep(JBCardLayout.SwipeDirection.FORWARD);
  }

  @Override
  protected int getNextStep(final int step) {
    AbstractWizardStepEx stepObject = myIndex2Step.get(step);
    Object nextStepId = stepObject.getNextStepId();
    return myStepId2Index.get(nextStepId);
  }

  @Override
  protected int getPreviousStep(final int step) {
    AbstractWizardStepEx stepObject = myIndex2Step.get(step);
    Object previousStepId = stepObject.getPreviousStepId();
    return myStepId2Index.get(previousStepId);
  }

  @Override
  protected String getHelpID() {
    return getCurrentStepObject().getHelpId();
  }


  @Override
  protected void updateStep() {
    super.updateStep();
    updateButtons();
    final AbstractWizardStepEx step = getCurrentStepObject();
    String stepTitle = step.getTitle();
    setTitle(stepTitle != null ? myTitle + ": " + stepTitle : myTitle);
    final JComponent toFocus = step.getPreferredFocusedComponent();
    if (toFocus != null) {
      IdeFocusManager.findInstanceByComponent(getWindow()).requestFocus(toFocus, true);
    }
  }

  @Override
  protected void updateButtons() {
    super.updateButtons();
    getPreviousButton().setEnabled(getCurrentStepObject().getPreviousStepId() != null);

    getNextButton().setEnabled(getCurrentStepObject().isComplete() && !isLastStep() || isLastStep() && canFinish());
  }

  @Override
  protected boolean canGoNext() {
    return getCurrentStepObject().isComplete();
  }

  @Override
  protected boolean isLastStep() {
    return myIndex2Step.get(myCurrentStep).getNextStepId() == null;
  }

  @Override
  protected boolean canFinish() {
    for (AbstractWizardStepEx step : mySteps) {
      if (!step.isComplete()) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected void dispose() {
    super.dispose();
    for (AbstractWizardStepEx step : mySteps) {
      Disposer.dispose(step);
    }
  }
}
