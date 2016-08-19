/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.refactoring.memberPullUp;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

/**
 * @author dsl
 * Date: 18.06.2002
 */
public class PullUpDialog extends PullUpDialogBase<MemberInfoStorage, MemberInfo, PsiMember, PsiClass> {
  private final Callback myCallback;
  private DocCommentPanel myJavaDocPanel;

  private final InterfaceContainmentVerifier myInterfaceContainmentVerifier = new InterfaceContainmentVerifier() {
    public boolean checkedInterfacesContain(PsiMethod psiMethod) {
      return PullUpProcessor.checkedInterfacesContain(myMemberInfos, psiMethod);
    }
  };

  private static final String PULL_UP_STATISTICS_KEY = "pull.up##";

  public interface Callback {
    boolean checkConflicts(PullUpDialog dialog);
  }

  public PullUpDialog(Project project, PsiClass aClass, List<PsiClass> superClasses, MemberInfoStorage memberInfoStorage, Callback callback) {
    super(project, aClass, superClasses, memberInfoStorage, JavaPullUpHandler.REFACTORING_NAME);
    myCallback = callback;

    init();
  }

  public int getJavaDocPolicy() {
    return myJavaDocPanel.getPolicy();
  }

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
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          if (myMemberSelectionPanel != null) {
            ((MyMemberInfoModel)myMemberInfoModel).setSuperClass(getSuperClass());
            myMemberSelectionPanel.getTable().setMemberInfos(myMemberInfos);
            myMemberSelectionPanel.getTable().fireExternalDataChange();
          }
        }
      }
    });
  }

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

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.MEMBERS_PULL_UP);
  }

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
    invokeRefactoring(new PullUpProcessor(myClass, superClass, infos.toArray(new MemberInfo[infos.size()]),
                                               new DocCommentPolicy(getJavaDocPolicy())));
    close(OK_EXIT_CODE);
  }

  @Override
  protected void addCustomElementsToCentralPanel(JPanel panel) {
    myJavaDocPanel = new DocCommentPanel(RefactoringBundle.message("javadoc.for.abstracts"));
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
    public MyMemberInfoModel() {
      super(myClass, getSuperClass(), false, myInterfaceContainmentVerifier);
    }

    @Override
    public boolean isMemberEnabled(MemberInfo member) {
      final PsiClass currentSuperClass = getSuperClass();
      if(currentSuperClass == null) return true;
      if (myMemberInfoStorage.getDuplicatedMemberInfos(currentSuperClass).contains(member)) return false;
      if (myMemberInfoStorage.getExtending(currentSuperClass).contains(member.getMember())) return false;
      final boolean isInterface = currentSuperClass.isInterface();
      if (!isInterface) return true;

      PsiElement element = member.getMember();
      if (element instanceof PsiClass && ((PsiClass) element).isInterface()) return true;
      if (element instanceof PsiField) {
        return ((PsiModifierListOwner) element).hasModifierProperty(PsiModifier.STATIC);
      }
      if (element instanceof PsiMethod) {
        final PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(currentSuperClass, myClass, PsiSubstitutor.EMPTY);
        final MethodSignature signature = ((PsiMethod) element).getSignature(superSubstitutor);
        final PsiMethod superClassMethod = MethodSignatureUtil.findMethodBySignature(currentSuperClass, signature, false);
        if (superClassMethod != null && !PsiUtil.isLanguageLevel8OrHigher(currentSuperClass)) return false;
        return !((PsiModifierListOwner) element).hasModifierProperty(PsiModifier.STATIC) || PsiUtil.isLanguageLevel8OrHigher(currentSuperClass);
      }
      return true;
    }

    @Override
    public boolean isAbstractEnabled(MemberInfo member) {
      PsiClass currentSuperClass = getSuperClass();
      if (currentSuperClass == null || !currentSuperClass.isInterface()) return true;
      if (PsiUtil.isLanguageLevel8OrHigher(currentSuperClass)) {
        return true;
      }
      return false;
    }

    @Override
    public boolean isAbstractWhenDisabled(MemberInfo member) {
      PsiClass currentSuperClass = getSuperClass();
      if(currentSuperClass == null) return false;
      if (currentSuperClass.isInterface()) {
        final PsiMember psiMember = member.getMember();
        if (psiMember instanceof PsiMethod) {
          return !psiMember.hasModifierProperty(PsiModifier.STATIC);
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
}
