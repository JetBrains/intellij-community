package com.intellij.codeInsight.generation.ui;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.generation.GenerateEqualsHelper;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.psi.*;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoChange;
import com.intellij.refactoring.util.classMembers.MemberInfoModel;
import com.intellij.refactoring.util.classMembers.MemberInfoTooltipManager;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Map;

/**
 * @author dsl
 */
public class GenerateEqualsWizard extends AbstractWizard {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.ui.GenerateEqualsWizard");
  private final PsiClass myClass;

  private final MemberSelectionPanel myEqualsPanel;
  private final MemberSelectionPanel myHashCodePanel;
  private final HashMap myFieldsToHashCode;
  private final MemberSelectionPanel myNonNullPanel;
  private final HashMap<PsiElement, MemberInfo> myFieldsToNonNull;

  private int myTestBoxedStep;

  private final MemberInfo[] myClassFields;
  private static final MyMemberInfoFilter MEMBER_INFO_FILTER = new MyMemberInfoFilter();


  public GenerateEqualsWizard(Project project, PsiClass aClass, boolean needEquals, boolean needHashCode) {
    super(CodeInsightBundle.message("generate.equals.hashcode.wizard.title"), project);
    LOG.assertTrue(needEquals || needHashCode);
    myClass = aClass;

    myClassFields = MemberInfo.extractClassMembers(myClass, MEMBER_INFO_FILTER, false);
    for (MemberInfo myClassField : myClassFields) {
      myClassField.setChecked(true);
    }
    myTestBoxedStep = 0;
    if (needEquals) {
      myEqualsPanel =
        new MemberSelectionPanel(CodeInsightBundle.message("generate.equals.hashcode.equals.fields.chooser.title"), myClassFields, null);
      myEqualsPanel.getTable().setMemberInfoModel(new EqualsMemberInfoModel());
      myTestBoxedStep++;
    }
    else {
      myEqualsPanel = null;
    }
    if (needHashCode) {
      final MemberInfo[] hashCodeMemberInfos;
      if (needEquals) {
        myFieldsToHashCode = createFieldToMemberInfoMap(true);
        hashCodeMemberInfos = new MemberInfo[0];
      }
      else {
        hashCodeMemberInfos = myClassFields;
        myFieldsToHashCode = null;
      }
      myHashCodePanel = new MemberSelectionPanel(CodeInsightBundle.message("generate.equals.hashcode.hashcode.fields.chooser.title"),
                                                 hashCodeMemberInfos, null);
      myHashCodePanel.getTable().setMemberInfoModel(new HashCodeMemberInfoModel());
      if (needEquals) {
        updateHashCodeMemberInfos(myClassFields);
      }
      myTestBoxedStep++;
    }
    else {
      myHashCodePanel = null;
      myFieldsToHashCode = null;
    }
    myNonNullPanel = new MemberSelectionPanel(CodeInsightBundle.message("generate.equals.hashcode.non.null.fields.chooser.title"),
                                              new MemberInfo[0], null);
    myFieldsToNonNull = createFieldToMemberInfoMap(false);
    for (final Map.Entry<PsiElement, MemberInfo> entry : myFieldsToNonNull.entrySet()) {
      entry.getValue().setChecked(((PsiField)entry.getKey()).getModifierList().findAnnotation(NotNull.class.getName()) != null);
    }

    final MyTableModelListener listener = new MyTableModelListener();
    if (myEqualsPanel != null) {
      myEqualsPanel.getTable().getModel().addTableModelListener(listener);
      addStep(new InstanceofOptionStep());
      addStep(new MyStep(myEqualsPanel));
    }
    if (myHashCodePanel != null) {
      myHashCodePanel.getTable().getModel().addTableModelListener(listener);
      addStep(new MyStep(myHashCodePanel));
    }
    addStep(new MyStep(myNonNullPanel));

    init();
    updateStatus();
  }

  public PsiField[] getEqualsFields() {
    if (myEqualsPanel != null) {
      return memberInfosToFields(myEqualsPanel.getTable().getSelectedMemberInfos());
    }
    else {
      return null;
    }
  }

  public PsiField[] getHashCodeFields() {
    if (myHashCodePanel != null) {
      return memberInfosToFields(myHashCodePanel.getTable().getSelectedMemberInfos());
    }
    else {
      return null;
    }
  }

  public PsiField[] getNonNullFields() {
    return memberInfosToFields(myNonNullPanel.getTable().getSelectedMemberInfos());
  }

  private static PsiField[] memberInfosToFields(MemberInfo[] infos) {
    ArrayList<PsiField> list = new ArrayList<PsiField>();
    for (MemberInfo info : infos) {
      list.add((PsiField)info.getMember());
    }
    return list.toArray(new PsiField[list.size()]);
  }

  protected void doNextAction() {
    switch (getCurrentStep()) {
      case 0:
        if (myEqualsPanel != null) {
          equalsFieldsSelected();
        }
        else {
          MemberInfo[] selectedMemberInfos = myHashCodePanel.getTable().getSelectedMemberInfos();
          updateNonNullMemberInfos(selectedMemberInfos);
        }
        break;
      default:
        break;
    }
    super.doNextAction();
    updateStatus();
  }

  protected void updateStep() {
    super.updateStep();
    final Component stepComponent = getCurrentStepComponent();
    if (stepComponent instanceof MemberSelectionPanel) {
      ((MemberSelectionPanel)stepComponent).getTable().requestFocus();
    }
  }

  protected String getHelpID() {
    return "editing.altInsert.equals";
  }

  private void equalsFieldsSelected() {
    MemberInfo[] selectedMemberInfos = myEqualsPanel.getTable().getSelectedMemberInfos();
    updateHashCodeMemberInfos(selectedMemberInfos);
    updateNonNullMemberInfos(selectedMemberInfos);
  }

  private HashMap<PsiElement, MemberInfo> createFieldToMemberInfoMap(boolean checkedByDefault) {
    MemberInfo[] memberInfos = MemberInfo.extractClassMembers(myClass, MEMBER_INFO_FILTER, false);
    final HashMap<PsiElement, MemberInfo> result = new HashMap<PsiElement, MemberInfo>();
    for (MemberInfo memberInfo : memberInfos) {
      memberInfo.setChecked(checkedByDefault);
      result.put(memberInfo.getMember(), memberInfo);
    }
    return result;
  }

  private void updateHashCodeMemberInfos(MemberInfo[] equalsMemberInfos) {
    if (myHashCodePanel == null) return;
    MemberInfo[] hashCodeFields = new MemberInfo[equalsMemberInfos.length];

    for (int i = 0; i < equalsMemberInfos.length; i++) {
      hashCodeFields[i] = (MemberInfo)myFieldsToHashCode.get(equalsMemberInfos[i].getMember());
    }
    myHashCodePanel.getTable().setMemberInfos(hashCodeFields);
  }

  private void updateNonNullMemberInfos(MemberInfo[] equalsMemberInfos) {
    final ArrayList<MemberInfo> list = new ArrayList<MemberInfo>();

    for (MemberInfo equalsMemberInfo : equalsMemberInfos) {
      PsiField field = (PsiField)equalsMemberInfo.getMember();
      if (!(field.getType() instanceof PsiPrimitiveType)) {
        list.add(myFieldsToNonNull.get(equalsMemberInfo.getMember()));
      }
    }
    myNonNullPanel.getTable().setMemberInfos(list.toArray(new MemberInfo[list.size()]));
  }

  private void updateStatus() {
    boolean finishEnabled = true;
    boolean nextEnabled = true;
    if (myEqualsPanel != null & getCurrentStep() == 0) {
      finishEnabled = false;
    }

    if (getCurrentStep() == myTestBoxedStep) {
      boolean anyNonBoxed = false;
      for (MemberInfo classField : myClassFields) {
        if (classField.isChecked()) {
          PsiField field = (PsiField)classField.getMember();
          if (!(field.getType() instanceof PsiPrimitiveType)) {
            anyNonBoxed = true;
            break;
          }
        }
      }
      finishEnabled &= !anyNonBoxed;
      nextEnabled = anyNonBoxed;
    }

    if (getCurrentStep() == 0) {
      boolean anyChecked = false;
      for (MemberInfo classField : myClassFields) {
        if (classField.isChecked()) {
          anyChecked = true;
          break;
        }
      }
      finishEnabled &= anyChecked;
      nextEnabled = !(!nextEnabled || !anyChecked);
    }

    if (getCurrentStep() == myTestBoxedStep) {
      finishEnabled = true;
      nextEnabled = false;
    }
    getFinishButton().setEnabled(finishEnabled);
    getNextButton().setEnabled(nextEnabled);

    if (finishEnabled) {
      getRootPane().setDefaultButton(getFinishButton());
    }
    else if (nextEnabled) {
      getRootPane().setDefaultButton(getNextButton());
    }
  }

  public JComponent getPreferredFocusedComponent() {
    final Component stepComponent = getCurrentStepComponent();
    if (stepComponent instanceof MemberSelectionPanel) {
      return ((MemberSelectionPanel)stepComponent).getTable();
    }
    else {
      return null;
    }
  }

  private class MyTableModelListener implements TableModelListener {
    public void tableChanged(TableModelEvent e) {
      updateStatus();
    }
  }

  private static class InstanceofOptionStep extends StepAdapter {
    private JComponent myPanel;

    private InstanceofOptionStep() {
      final JCheckBox checkbox = new NonFocusableCheckBox(CodeInsightBundle.message("generate.equals.hashcode.accept.sublcasses"));
      checkbox.setSelected(CodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER);
      checkbox.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          CodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER = checkbox.isSelected();
        }
      });

      myPanel = new JPanel(new VerticalFlowLayout());
      myPanel.add(checkbox);
      myPanel.add(new JLabel(CodeInsightBundle.message("generate.equals.hashcode.accept.sublcasses.explanation")));
    }

    public JComponent getComponent() {
      return myPanel;
    }

    @Nullable
    public Icon getIcon() {
      return null;
    }
  }

  private static class MyStep extends StepAdapter {
    final MemberSelectionPanel myPanel;

    public MyStep(MemberSelectionPanel panel) {
      myPanel = panel;
    }

    public Icon getIcon() {
      return null;
    }

    public JComponent getComponent() {
      return myPanel;
    }

  }

  private static class MyMemberInfoFilter implements MemberInfo.Filter {
    public boolean includeMember(PsiMember element) {
      return element instanceof PsiField && !element.hasModifierProperty(PsiModifier.STATIC);
    }
  }


  private static class EqualsMemberInfoModel implements MemberInfoModel {
    MemberInfoTooltipManager myTooltipManager = new MemberInfoTooltipManager(new MemberInfoTooltipManager.TooltipProvider() {
      public String getTooltip(MemberInfo memberInfo) {
        if (checkForProblems(memberInfo) == OK) return null;
        if (!(memberInfo.getMember() instanceof PsiField)) return CodeInsightBundle.message("generate.equals.hashcode.internal.error");
        final PsiType type = ((PsiField)memberInfo.getMember()).getType();
        if (GenerateEqualsHelper.isNestedArray(type)) {
          return CodeInsightBundle .message("generate.equals.warning.equals.for.nested.arrays.not.supported");
        }
        if (GenerateEqualsHelper.isArrayOfObjects(type)) {
          return CodeInsightBundle.message("generate.equals.warning.generated.equals.could.be.incorrect");
        }
        return null;
      }
    });

    public boolean isMemberEnabled(MemberInfo member) {
      if (!(member.getMember() instanceof PsiField)) return false;
      final PsiType type = ((PsiField)member.getMember()).getType();
      return !GenerateEqualsHelper.isNestedArray(type);
    }

    public boolean isCheckedWhenDisabled(MemberInfo member) {
      return false;
    }

    public boolean isAbstractEnabled(MemberInfo member) {
      return false;
    }

    public boolean isAbstractWhenDisabled(MemberInfo member) {
      return false;
    }

    public Boolean isFixedAbstract(MemberInfo member) {
      return null;
    }

    public int checkForProblems(@NotNull MemberInfo member) {
      if (!(member.getMember() instanceof PsiField)) return ERROR;
      final PsiType type = ((PsiField)member.getMember()).getType();
      if (GenerateEqualsHelper.isNestedArray(type)) return ERROR;
      if (GenerateEqualsHelper.isArrayOfObjects(type)) return WARNING;
      return OK;
    }

    public void memberInfoChanged(MemberInfoChange event) {
    }

    public String getTooltipText(MemberInfo member) {
      return myTooltipManager.getTooltip(member);
    }
  }

  private static class HashCodeMemberInfoModel implements MemberInfoModel {
    private MemberInfoTooltipManager myTooltipManager = new MemberInfoTooltipManager(new MemberInfoTooltipManager.TooltipProvider() {
      public String getTooltip(MemberInfo memberInfo) {
        if (isMemberEnabled(memberInfo)) return null;
        if (!(memberInfo.getMember() instanceof PsiField)) return CodeInsightBundle.message("generate.equals.hashcode.internal.error");
        final PsiType type = ((PsiField)memberInfo.getMember()).getType();
        if (!(type instanceof PsiArrayType)) return null;
        return CodeInsightBundle.message("generate.equals.hashcode.warning.hashcode.for.arrays.is.not.supported");
      }
    });

    public boolean isMemberEnabled(MemberInfo member) {
      final PsiMember psiMember = member.getMember();
      return psiMember instanceof PsiField;
    }

    public boolean isCheckedWhenDisabled(MemberInfo member) {
      return false;
    }

    public boolean isAbstractEnabled(MemberInfo member) {
      return false;
    }

    public boolean isAbstractWhenDisabled(MemberInfo member) {
      return false;
    }

    public Boolean isFixedAbstract(MemberInfo member) {
      return null;
    }

    public int checkForProblems(@NotNull MemberInfo member) {
      return OK;
    }

    public void memberInfoChanged(MemberInfoChange event) {
    }

    public String getTooltipText(MemberInfo member) {
      return myTooltipManager.getTooltip(member);
    }
  }
}
