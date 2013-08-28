package com.intellij.codeInsight.generation.ui;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.ide.wizard.Step;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.ui.AbstractMemberSelectionPanel;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.awt.*;
import java.util.Collection;
import java.util.List;

/**
 * Nikolay.Tropin
 * 8/20/13
 */
public abstract class AbstractGenerateEqualsWizard <C extends PsiElement, M extends PsiElement, I extends MemberInfoBase<M>> 
  extends AbstractWizard<Step> {
  
  protected final C myClass;

  protected final AbstractMemberSelectionPanel<M, I> myEqualsPanel;
  protected final AbstractMemberSelectionPanel<M, I> myHashCodePanel;
  protected final AbstractMemberSelectionPanel<M, I> myNonNullPanel;
  protected final HashMap<M, I> myFieldsToHashCode;
  protected final HashMap<M, I> myFieldsToNonNull;

  private int myNonNullStepCode;
  private int myEqualsStepCode;
  private int myHashCodeStepCode;

  protected int getHashCodeStepCode() {
    return myHashCodeStepCode;
  }

  protected int getEqualsStepCode() {
    return myEqualsStepCode;
  }

  protected int getNonNullStepCode() {
    return myNonNullStepCode;
  }

  protected final List<I> myClassFields;

  protected final Builder<C, M, I> myBuilder;

  public static abstract class Builder<C extends PsiElement, M extends PsiElement, I extends MemberInfoBase<M>> {
    protected abstract C getPsiClass();
    protected abstract List<I> getClassFields();
    protected abstract HashMap<M, I> getFieldsToHashCode();
    protected abstract HashMap<M, I> getFieldsToNonNull();
    protected abstract AbstractMemberSelectionPanel<M, I> getEqualsPanel();
    protected abstract AbstractMemberSelectionPanel<M, I> getHashCodePanel();
    protected abstract AbstractMemberSelectionPanel<M, I> getNonNullPanel();
    protected abstract void updateHashCodeMemberInfos(Collection<I> equalsMemberInfos);
    protected abstract void updateNonNullMemberInfos(Collection<I> equalsMemberInfos);
  }

  public AbstractGenerateEqualsWizard(Project project, Builder<C, M, I> builder) {
    super(CodeInsightBundle.message("generate.equals.hashcode.wizard.title"), project);
    myBuilder = builder;
    myClass = builder.getPsiClass();
    myClassFields = builder.getClassFields();
    myFieldsToHashCode = builder.getFieldsToHashCode();
    myFieldsToNonNull = builder.getFieldsToNonNull();
    myEqualsPanel = builder.getEqualsPanel();
    myHashCodePanel = builder.getHashCodePanel();
    myNonNullPanel = builder.getNonNullPanel();

    addTableListeners();
    addSteps();
    init();
    updateButtons();
  }

  protected void addSteps() {
    myEqualsStepCode = addStepForPanel(myEqualsPanel);
    myHashCodeStepCode = addStepForPanel(myHashCodePanel);
    myNonNullStepCode = addStepForPanel(myNonNullPanel);
  }

  protected int addStepForPanel(AbstractMemberSelectionPanel<M, I> panel) {
    if (panel != null) {
      addStep(new MyStep(panel));
      return getStepCount() - 1;
    } else {
      return -1;
    }
  }

  protected void addTableListeners() {
    final MyTableModelListener listener = new MyTableModelListener();
    if (myEqualsPanel != null) myEqualsPanel.getTable().getModel().addTableModelListener(listener);
    if (myHashCodePanel != null) myHashCodePanel.getTable().getModel().addTableModelListener(listener);
  }

  @Override
  protected void doNextAction() {
    if (getCurrentStep() == getEqualsStepCode() && myEqualsPanel != null) {
      equalsFieldsSelected();
    }
    else if (getCurrentStep() == getHashCodeStepCode() && myHashCodePanel != null) {
      Collection<I> selectedMemberInfos = myEqualsPanel != null ? myEqualsPanel.getTable().getSelectedMemberInfos()
                                                                : myHashCodePanel.getTable().getSelectedMemberInfos();
      updateNonNullMemberInfos(selectedMemberInfos);
    }

    super.doNextAction();
    updateButtons();
  }

  @Override
  protected String getHelpID() {
    return "editing.altInsert.equals";
  }

  private void equalsFieldsSelected() {
    Collection<I> selectedMemberInfos = myEqualsPanel.getTable().getSelectedMemberInfos();
    updateHashCodeMemberInfos(selectedMemberInfos);
    updateNonNullMemberInfos(selectedMemberInfos);
  }

  @Override
  protected void doOKAction() {
    if (myEqualsPanel != null) {
      equalsFieldsSelected();
    }
    super.doOKAction();
  }

  protected void updateHashCodeMemberInfos(Collection<I> equalsMemberInfos) {
    myBuilder.updateHashCodeMemberInfos(equalsMemberInfos);
  }

  protected void updateNonNullMemberInfos(Collection<I> equalsMemberInfos) {
    myBuilder.updateNonNullMemberInfos(equalsMemberInfos);
  }

  @Override
  protected boolean canGoNext() {
    if (getCurrentStep() == myEqualsStepCode) {
      for (I classField : myClassFields) {
        if (classField.isChecked()) {
          return true;
        }
      }
      return false;
    }

    return true;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    final Component stepComponent = getCurrentStepComponent();
    if (stepComponent instanceof AbstractMemberSelectionPanel) {
      return ((AbstractMemberSelectionPanel)stepComponent).getTable();
    }
    else {
      return null;
    }
  }

  private class MyTableModelListener implements TableModelListener {
    public void tableChanged(TableModelEvent modelEvent) {
      updateButtons();
    }
  }

  private static class MyStep extends StepAdapter {
    final AbstractMemberSelectionPanel myPanel;

    public MyStep(AbstractMemberSelectionPanel panel) {
      myPanel = panel;
    }

    @Override
    public Icon getIcon() {
      return null;
    }

    @Override
    public JComponent getComponent() {
      return myPanel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myPanel.getTable();
    }
  }
}
