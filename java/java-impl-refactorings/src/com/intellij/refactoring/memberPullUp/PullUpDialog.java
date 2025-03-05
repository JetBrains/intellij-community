// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.memberPullUp;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.ui.AbstractMemberSelectionTable;
import com.intellij.refactoring.ui.ClassCellRenderer;
import com.intellij.refactoring.ui.DocCommentPanel;
import com.intellij.refactoring.ui.MemberSelectionTable;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.classMembers.InterfaceContainmentVerifier;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.refactoring.util.classMembers.UsesAndInterfacesDependencyMemberInfoModel;
import com.intellij.util.ui.UIUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

public class PullUpDialog extends PullUpDialogBase<MemberInfoStorage, MemberInfo, PsiMember, PsiClass> {
  private final Callback myCallback;
  private DocCommentPanel myJavaDocPanel;

  private final InterfaceContainmentVerifier myInterfaceContainmentVerifier = new InterfaceContainmentVerifier() {
    @Override
    public boolean checkedInterfacesContain(PsiMethod psiMethod) {
      return PullUpProcessor.checkedInterfacesContain(getMemberInfos(), psiMethod);
    }
  };

  private static final @NonNls String PULL_UP_STATISTICS_KEY = "pull.up##";

  static boolean isEnumSyntheticMethod(@NotNull PsiMethod method) {
    return CallMatcher.enumValues().methodMatches(method) ||
           CallMatcher.enumValueOf().methodMatches(method);
  }

  public interface Callback {
    boolean checkConflicts(PullUpDialog dialog);
  }

  public PullUpDialog(Project project, PsiClass aClass, List<PsiClass> superClasses, MemberInfoStorage memberInfoStorage, Callback callback) {
    super(project, aClass, superClasses, memberInfoStorage, JavaPullUpHandler.getRefactoringName());
    myCallback = callback;

    init();
  }

  private List<MemberInfo> getMemberInfos() {
    return myMemberInfos;
  }

  public int getJavaDocPolicy() {
    return myJavaDocPanel.getPolicy();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.memberPullUp.PullUpDialog";
  }

  InterfaceContainmentVerifier getContainmentVerifier() {
    return myInterfaceContainmentVerifier;
  }

  @Override
  protected void initClassCombo(JComboBox classCombo) {
    classCombo.setRenderer(new ClassCellRenderer(classCombo.getRenderer()));
    classCombo.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          updateMemberPanels();
        }
      }
    });
  }

  private void updateMemberPanels() {
    if (myMemberSelectionPanel != null) {
      ((MyMemberInfoModel)myMemberInfoModel).setSuperClass(getSuperClass());
      myMemberSelectionPanel.getTable().setMemberInfos(myMemberInfos);
      myMemberSelectionPanel.getTable().fireExternalDataChange();
    }
  }

  @Override
  protected PsiClass getPreselection() {
    PsiClass preselection = RefactoringHierarchyUtil.getNearestBaseClass(myClass, false);

    final String statKey = PULL_UP_STATISTICS_KEY + myClass.getQualifiedName();
    for (StatisticsInfo info : StatisticsManager.getInstance().getAllValues(statKey)) {
      final String superClassName = info.getValue();
      PsiClass superClass = null;
      for (PsiClass aClass : mySuperClasses) {
        if (Comparing.strEqual(superClassName, aClass.getQualifiedName())) {
          superClass = aClass;
          break;
        }
      }
      if (superClass != null && StatisticsManager.getInstance().getUseCount(info) > 0) {
        preselection = superClass;
        break;
      }
    }
    return preselection;
  }

  @Override
  protected String getHelpId() {
    return HelpID.MEMBERS_PULL_UP;
  }

  @Override
  protected void doAction() {
    if (!myCallback.checkConflicts(this)) return;
    JavaRefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC = myJavaDocPanel.getPolicy();
    final PsiClass superClass = getSuperClass();
    String name = superClass.getQualifiedName();
    if (name != null) {
      StatisticsManager
        .getInstance().incUseCount(new StatisticsInfo(PULL_UP_STATISTICS_KEY + myClass.getQualifiedName(), name));
    }

    List<MemberInfo> infos = getSelectedMemberInfos();
    invokeRefactoring(new PullUpProcessor(myClass, superClass, infos.toArray(new MemberInfo[0]),
                                               new DocCommentPolicy(getJavaDocPolicy())));
    close(OK_EXIT_CODE);
  }

  @Override
  protected void addCustomElementsToCentralPanel(JPanel panel) {
    myJavaDocPanel = new DocCommentPanel(JavaRefactoringBundle.message("javadoc.for.abstracts"));
    myJavaDocPanel.setPolicy(JavaRefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC);
    panel.add(myJavaDocPanel, BorderLayout.EAST);
    updateAbstractState();
  }

  private void updateAbstractState() {
    boolean hasJavadoc = false;
    for (MemberInfo info : myMemberInfos) {
      final PsiMember member = info.getMember();
      final boolean abstractWhenDisabled = myMemberInfoModel.isAbstractWhenDisabled(info);
      info.setToAbstract(abstractWhenDisabled);
      if (myMemberInfoModel.isAbstractEnabled(info) || abstractWhenDisabled) {
        if (!hasJavadoc &&
            member instanceof PsiDocCommentOwner &&
            ((PsiDocCommentOwner)member).getDocComment() != null) {
          hasJavadoc = true;
        }
      }
    }
    UIUtil.setEnabled(myJavaDocPanel, hasJavadoc, true);
  }

  @Override
  protected void updateMemberInfo() {
    super.updateMemberInfo();
    updateAbstractState();
  }

  @Override
  protected AbstractMemberSelectionTable<PsiMember, MemberInfo> createMemberSelectionTable(List<MemberInfo> infos) {
    return new MemberSelectionTable(infos, RefactoringBundle.message("make.abstract"));
  }

  @Override
  protected MemberInfoModel<PsiMember, MemberInfo> createMemberInfoModel() {
    return new MyMemberInfoModel();
  }

  private class MyMemberInfoModel extends UsesAndInterfacesDependencyMemberInfoModel<PsiMember, MemberInfo> {
    MyMemberInfoModel() {
      super(getPsiClass(), getSuperClass(), false, myInterfaceContainmentVerifier);
    }

    @Override
    public boolean isMemberEnabled(MemberInfo member) {
      final PsiClass currentSuperClass = getSuperClass();
      if(currentSuperClass == null) return true;
      if (getMemberInfoStorage().getDuplicatedMemberInfos(currentSuperClass).contains(member)) return false;
      if (getMemberInfoStorage().getExtending(currentSuperClass).contains(member.getMember())) return false;
      final boolean isInterface = currentSuperClass.isInterface();
      if (!isInterface) return true;

      PsiModifierListOwner element = member.getMember();
      if (element instanceof PsiClass && ((PsiClass) element).isInterface()) return true;
      if (element instanceof PsiEnumConstant) {
        return false;
      }
      if (element instanceof PsiField) {
        return element.hasModifierProperty(PsiModifier.STATIC);
      }
      if (element instanceof PsiMethod method) {
        PsiClass aClass = method.getContainingClass();
        if (aClass != null && aClass.isEnum() && isEnumSyntheticMethod(method)) {
          return false;
        }
        final PsiMethod superClassMethod = findSuperMethod(currentSuperClass, method);
        if (superClassMethod != null && !PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, currentSuperClass)) return false;
        return !element.hasModifierProperty(PsiModifier.STATIC) || PsiUtil.isAvailable(JavaFeature.STATIC_INTERFACE_CALLS, currentSuperClass);
      }
      if (element instanceof PsiClassInitializer) {
        return false;
      }
      return true;
    }

    private PsiMethod findSuperMethod(PsiClass currentSuperClass, PsiMethod element) {
      final PsiSubstitutor superSubstitutor =
        TypeConversionUtil.getSuperClassSubstitutor(currentSuperClass, getPsiClass(), PsiSubstitutor.EMPTY);
      final MethodSignature signature = element.getSignature(superSubstitutor);
      return MethodSignatureUtil.findMethodBySignature(currentSuperClass, signature, false);
    }

    @Override
    public boolean isAbstractEnabled(MemberInfo member) {
      if (member.getMember() instanceof PsiMethod method && JavaPsiRecordUtil.getRecordComponentForAccessor(method) != null) {
        return false;
      }
      PsiClass currentSuperClass = getSuperClass();
      if (currentSuperClass == null || !currentSuperClass.isInterface()) return true;
      if (PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, currentSuperClass)) {
        return true;
      }
      return false;
    }

    @Override
    public boolean isAbstractWhenDisabled(MemberInfo member) {
      PsiClass currentSuperClass = getSuperClass();
      if(currentSuperClass == null) return false;
      if (currentSuperClass.isInterface()) {
        if (!PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, currentSuperClass)) {
          return true;
        }
        if (member.getMember() instanceof PsiMethod method) {
          if (JavaPsiRecordUtil.getRecordComponentForAccessor(method) != null) {
            return true;
          }
          return !method.hasModifierProperty(PsiModifier.STATIC) && findSuperMethod(currentSuperClass, method) == null;
        }
      }
      return false;
    }

    @Override
    public int checkForProblems(@NotNull MemberInfo member) {
      if (member.isChecked()) return OK;
      PsiClass currentSuperClass = getSuperClass();

      if (currentSuperClass != null && currentSuperClass.isInterface()) {
        PsiMember element = member.getMember();
        if (element.hasModifierProperty(PsiModifier.STATIC)) {
          return super.checkForProblems(member);
        }
        return OK;
      }
      else {
        return super.checkForProblems(member);
      }
    }

    @Override
    public Boolean isFixedAbstract(MemberInfo member) {
      return Boolean.TRUE;
    }
  }

  private PsiClass getPsiClass() {
    return myClass;
  }

  private MemberInfoStorage getMemberInfoStorage() {
    return myMemberInfoStorage;
  }
}
