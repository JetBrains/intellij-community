// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.move.moveMembers;

import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.PackageUtil;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoChange;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveDialogBase;
import com.intellij.refactoring.ui.JavaVisibilityPanel;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.ui.MemberSelectionTable;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.UsesAndInterfacesDependencyMemberInfoModel;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class MoveMembersDialog extends MoveDialogBase implements MoveMembersOptions {
  private static final String RECENTS_KEY = "MoveMembersDialog.RECENTS_KEY";

  private final PsiClass mySourceClass;
  private final String mySourceClassName;
  private final List<MemberInfo> myMemberInfos;
  private final ReferenceEditorComboWithBrowseButton myTfTargetClassName;
  private final MoveCallback myMoveCallback;

  private MyMemberInfoModel myMemberInfoModel;
  private MemberSelectionTable myTable;
  private JavaVisibilityPanel myVisibilityPanel;
  private final JCheckBox myIntroduceEnumConstants = new JCheckBox(JavaRefactoringBundle.message("move.enum.constant.cb"), true);

  @Override
  protected @NotNull String getRefactoringId() {
    return "MoveMember";
  }

  public MoveMembersDialog(Project project,
                           PsiClass sourceClass,
                           final PsiClass initialTargetClass,
                           Set<PsiMember> preselectMembers,
                           MoveCallback moveCallback) {
    super(project, true, true);
    mySourceClass = sourceClass;
    myMoveCallback = moveCallback;
    setTitle(MoveMembersImpl.getRefactoringName());

    mySourceClassName = mySourceClass.getQualifiedName();

    PsiField[] fields = mySourceClass.getFields();
    PsiMethod[] methods = mySourceClass.getMethods();
    PsiClass[] innerClasses = mySourceClass.getInnerClasses();
    ArrayList<MemberInfo> memberList = new ArrayList<>(fields.length + methods.length);

    for (PsiClass innerClass : innerClasses) {
      if (!innerClass.hasModifierProperty(PsiModifier.STATIC)) continue;
      MemberInfo info = new MemberInfo(innerClass);
      if (preselectMembers.contains(innerClass)) {
        info.setChecked(true);
      }
      memberList.add(info);
    }
    boolean hasConstantFields = false;
    for (PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        MemberInfo info = new MemberInfo(field);
        if (preselectMembers.contains(field)) {
          info.setChecked(true);
        }
        memberList.add(info);
        hasConstantFields = true;
      }
    }
    if (!hasConstantFields) myIntroduceEnumConstants.setVisible(false);
    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        MemberInfo info = new MemberInfo(method);
        if (preselectMembers.contains(method)) {
          info.setChecked(true);
        }
        memberList.add(info);
      }
    }
    myMemberInfos = memberList;
    String fqName = initialTargetClass != null && !sourceClass.equals(initialTargetClass) ? initialTargetClass.getQualifiedName() : "";
    myTfTargetClassName = new ReferenceEditorComboWithBrowseButton(new ChooseClassAction(), fqName, myProject, true, JavaCodeFragment.VisibilityChecker.PROJECT_SCOPE_VISIBLE, RECENTS_KEY);

    init();
  }

  @Override
  @Nullable
  public String getMemberVisibility() {
    return myVisibilityPanel.getVisibility();
  }

  @Override
  public boolean makeEnumConstant() {
    return myIntroduceEnumConstants.isVisible() && myIntroduceEnumConstants.isEnabled() && myIntroduceEnumConstants.isSelected();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.move.moveMembers.MoveMembersDialog";
  }

  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    JPanel _panel;
    Box box = Box.createVerticalBox();

    _panel = new JPanel(new BorderLayout());
    JTextField sourceClassField = new JTextField();
    sourceClassField.setText(mySourceClassName);
    sourceClassField.setEditable(false);
    _panel.add(new JLabel(RefactoringBundle.message("move.members.move.members.from.label")), BorderLayout.NORTH);
    _panel.add(sourceClassField, BorderLayout.CENTER);
    box.add(_panel);

    box.add(Box.createVerticalStrut(10));

    _panel = new JPanel(new BorderLayout());
    JLabel label = new JLabel(RefactoringBundle.message("move.members.to.fully.qualified.name.label"));
    label.setLabelFor(myTfTargetClassName);
    _panel.add(label, BorderLayout.NORTH);
    _panel.add(myTfTargetClassName, BorderLayout.CENTER);
    _panel.add(myIntroduceEnumConstants, BorderLayout.SOUTH);
    box.add(_panel);

    myTfTargetClassName.getChildComponent().getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        myMemberInfoModel.updateTargetClass();
        validateButtons();
      }
    });

    panel.add(box, BorderLayout.CENTER);
    panel.add(Box.createVerticalStrut(10), BorderLayout.SOUTH);

    validateButtons();
    return panel;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    final String title = RefactoringBundle.message("move.members.members.to.be.moved.border.title");
    final MemberSelectionPanel selectionPanel = new MemberSelectionPanel(title, myMemberInfos, null);
    myTable = selectionPanel.getTable();
    myMemberInfoModel = new MyMemberInfoModel();
    myMemberInfoModel.memberInfoChanged(new MemberInfoChange<>(myMemberInfos));
    selectionPanel.getTable().setMemberInfoModel(myMemberInfoModel);
    selectionPanel.getTable().addMemberInfoChangeListener(myMemberInfoModel);
    panel.add(selectionPanel, BorderLayout.CENTER);

    myVisibilityPanel = new JavaVisibilityPanel(true, true);
    myVisibilityPanel.setVisibility(null);
    panel.add(myVisibilityPanel, BorderLayout.EAST);

    return panel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTfTargetClassName.getChildComponent();
  }

  @Override
  public PsiMember[] getSelectedMembers() {
    final Collection<MemberInfo> selectedMemberInfos = myTable.getSelectedMemberInfos();
    ArrayList<PsiMember> list = new ArrayList<>();
    for (MemberInfo selectedMemberInfo : selectedMemberInfos) {
      list.add(selectedMemberInfo.getMember());
    }
    return list.toArray(PsiMember.EMPTY_ARRAY);
  }

  @Override
  public String getTargetClassName() {
    return myTfTargetClassName.getText();
  }

  @Override
  protected void doAction() {
    String message = validateInputData();

    if (message != null) {
      if (message.length() != 0) {
        CommonRefactoringUtil.showErrorMessage(
          MoveMembersImpl.getRefactoringName(),
          message,
          HelpID.MOVE_MEMBERS,
          myProject);
      }
      return;
    }

    invokeRefactoring(new MoveMembersProcessor(getProject(), myMoveCallback, new MoveMembersOptions() {
      @Override
      public String getMemberVisibility() {
        return MoveMembersDialog.this.getMemberVisibility();
      }

      @Override
      public boolean makeEnumConstant() {
        return MoveMembersDialog.this.makeEnumConstant();
      }

      @Override
      public PsiMember[] getSelectedMembers() {
        return MoveMembersDialog.this.getSelectedMembers();
      }

      @Override
      public String getTargetClassName() {
        return MoveMembersDialog.this.getTargetClassName();
      }
    }, isOpenInEditor()));
  }

  @Override
  protected void canRun() throws ConfigurationException {
    //if (getTargetClassName().length() == 0) throw new ConfigurationException("Destination class name not found");
  }

  @Nullable
  private @NlsContexts.DialogMessage String validateInputData() {
    final PsiManager manager = PsiManager.getInstance(myProject);
    final String fqName = getTargetClassName();
    if (fqName != null && fqName.isEmpty()) {
      return RefactoringBundle.message("no.destination.class.specified");
    }
    else {
      if (!PsiNameHelper.getInstance(manager.getProject()).isQualifiedName(fqName)) {
        return RefactoringBundle.message("0.is.not.a.legal.fq.name", fqName);
      }
      else {
        RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, fqName);
        final PsiClass[] targetClass = new PsiClass[]{null};
        CommandProcessor.getInstance().executeCommand(myProject, () -> {
          try {
            targetClass[0] = findOrCreateTargetClass(manager, fqName);
          }
          catch (IncorrectOperationException e) {
            CommonRefactoringUtil.showErrorMessage(
              MoveMembersImpl.getRefactoringName(),
              e.getMessage(),
              HelpID.MOVE_MEMBERS,
              myProject);
          }
        }, RefactoringBundle.message("create.class.command", fqName), null);

        if (targetClass[0] == null) {
          return "";
        }

        if (mySourceClass.equals(targetClass[0])) {
          return RefactoringBundle.message("source.and.destination.classes.should.be.different");
        } else if (!mySourceClass.getLanguage().equals(targetClass[0].getLanguage())) {
          return RefactoringBundle.message("move.to.different.language", UsageViewUtil.getType(mySourceClass), mySourceClass.getQualifiedName(), targetClass[0].getQualifiedName());
        }
        else {
          for (MemberInfo info : myMemberInfos) {
            if (!info.isChecked()) continue;
            if (PsiTreeUtil.isAncestor(info.getMember(), targetClass[0], false)) {
              return JavaRefactoringBundle.message("cannot.move.inner.class.0.into.itself", info.getDisplayName());
            }
          }

          if (!targetClass[0].isWritable()) {
            CommonRefactoringUtil.checkReadOnlyStatus(myProject, targetClass[0]);
            return "";
          }

          return null;
        }
      }
    }
  }

  @Nullable
  private PsiClass findOrCreateTargetClass(final PsiManager manager, final String fqName) throws IncorrectOperationException {
    final String className;
    final String packageName;
    int dotIndex = fqName.lastIndexOf('.');
    if (dotIndex >= 0) {
      packageName = fqName.substring(0, dotIndex);
      className = (dotIndex + 1 < fqName.length())? fqName.substring(dotIndex + 1) : "";
    }
    else {
      packageName = "";
      className = fqName;
    }


    PsiClass aClass = 
      ActionUtil.underModalProgress(myProject, JavaBundle.message("progress.title.checking.if.class.exists", fqName), 
                                    () -> JavaPsiFacade.getInstance(manager.getProject()).findClass(fqName, GlobalSearchScope.projectScope(myProject)));
    if (aClass != null) return aClass;

    final PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(
                myProject,
                packageName,
                mySourceClass.getContainingFile().getContainingDirectory(),
                true);

    if (directory == null) {
      return null;
    }

    int answer = Messages.showYesNoDialog(
      myProject,
      RefactoringBundle.message("class.0.does.not.exist", fqName),
      MoveMembersImpl.getRefactoringName(),
      Messages.getQuestionIcon()
    );
    if (answer != Messages.YES) return null;
    final Ref<IncorrectOperationException> eRef = new Ref<>();
    final PsiClass newClass = WriteAction.compute(() -> {
      try {
        return JavaDirectoryService.getInstance().createClass(directory, className);
      }
      catch (IncorrectOperationException e) {
        eRef.set(e);
        return null;
      }
    });
    if (!eRef.isNull()) throw eRef.get();
    return newClass;
  }

  @Override
  protected String getHelpId() {
    return HelpID.MOVE_MEMBERS;
  }

  private class ChooseClassAction implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createWithInnerClassesScopeChooser(
        RefactoringBundle.message("choose.destination.class"), GlobalSearchScope.projectScope(myProject), new ClassFilter() {
        @Override
        public boolean isAccepted(PsiClass aClass) {
          return aClass.getParent() instanceof PsiFile || aClass.hasModifierProperty(PsiModifier.STATIC);
        }
      }, null);
      final String targetClassName = getTargetClassName();
      if (targetClassName != null) {
        final PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(targetClassName, GlobalSearchScope.allScope(myProject));
        if (aClass != null) {
          chooser.selectDirectory(aClass.getContainingFile().getContainingDirectory());
        } else {
          chooser.selectDirectory(mySourceClass.getContainingFile().getContainingDirectory());
        }
      }

      chooser.showDialog();
      PsiClass aClass = chooser.getSelected();
      if (aClass != null) {
        myTfTargetClassName.setText(aClass.getQualifiedName());
        myMemberInfoModel.updateTargetClass();
      }
    }
  }

  private class MyMemberInfoModel extends UsesAndInterfacesDependencyMemberInfoModel<PsiMember, MemberInfo> {
    PsiClass myTargetClass;
    MyMemberInfoModel() {
      super(mySourceClass, null, false, DEFAULT_CONTAINMENT_VERIFIER);
    }

    @Override
    @Nullable
    public Boolean isFixedAbstract(MemberInfo member) {
      return null;
    }

    @Override
    public boolean isCheckedWhenDisabled(MemberInfo member) {
      return false;
    }

    @Override
    public boolean isMemberEnabled(MemberInfo member) {
      if(myTargetClass != null && myTargetClass.isInterface() && !PsiUtil.isLanguageLevel8OrHigher(myTargetClass)) {
        return !(member.getMember() instanceof PsiMethod);
      }
      return super.isMemberEnabled(member);
    }

    public void updateTargetClass() {
      final PsiManager manager = PsiManager.getInstance(myProject);
      myTargetClass =
        JavaPsiFacade.getInstance(manager.getProject()).findClass(getTargetClassName(), GlobalSearchScope.projectScope(myProject));
      myTable.fireExternalDataChange();
      myIntroduceEnumConstants.setEnabled(myTargetClass != null && myTargetClass.isEnum());
    }
  }
}