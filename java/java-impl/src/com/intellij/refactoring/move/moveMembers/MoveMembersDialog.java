/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.move.moveMembers;

import com.intellij.ide.util.PackageUtil;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoChange;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.ui.JavaVisibilityPanel;
import com.intellij.refactoring.ui.MemberSelectionTable;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.UsesAndInterfacesDependencyMemberInfoModel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class MoveMembersDialog extends RefactoringDialog implements MoveMembersOptions {
  @NonNls private static final String RECENTS_KEY = "MoveMembersDialog.RECENTS_KEY";
  private MyMemberInfoModel myMemberInfoModel;

  private final Project myProject;
  private final PsiClass mySourceClass;
  private final String mySourceClassName;
  private final List<MemberInfo> myMemberInfos;
  private final ReferenceEditorComboWithBrowseButton myTfTargetClassName;
  private MemberSelectionTable myTable;
  private final MoveCallback myMoveCallback;

  JavaVisibilityPanel myVisibilityPanel;
  private final JCheckBox myIntroduceEnumConstants = new JCheckBox(RefactoringBundle.message("move.enum.constant.cb"), true);

  public MoveMembersDialog(Project project,
                           PsiClass sourceClass,
                           final PsiClass initialTargetClass,
                           Set<PsiMember> preselectMembers,
                           MoveCallback moveCallback) {
    super(project, true);
    myProject = project;
    mySourceClass = sourceClass;
    myMoveCallback = moveCallback;
    setTitle(MoveMembersImpl.REFACTORING_NAME);

    mySourceClassName = mySourceClass.getQualifiedName();

    PsiField[] fields = mySourceClass.getFields();
    PsiMethod[] methods = mySourceClass.getMethods();
    PsiClass[] innerClasses = mySourceClass.getInnerClasses();
    ArrayList<MemberInfo> memberList = new ArrayList<MemberInfo>(fields.length + methods.length);

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
    myTfTargetClassName = new ReferenceEditorComboWithBrowseButton(new ChooseClassAction(), fqName, PsiManager.getInstance(myProject), true, RECENTS_KEY);

    init();
  }

  public String getMemberVisibility() {
    return myVisibilityPanel.getVisibility();
  }

  public boolean makeEnumConstant() {
    return myIntroduceEnumConstants.isVisible() && myIntroduceEnumConstants.isEnabled() && myIntroduceEnumConstants.isSelected();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.move.moveMembers.MoveMembersDialog";
  }

  private JTable createTable() {
    myMemberInfoModel = new MyMemberInfoModel();
    myTable = new MemberSelectionTable(myMemberInfos, null);
    myTable.setMemberInfoModel(myMemberInfoModel);
    myTable.addMemberInfoChangeListener(myMemberInfoModel);
    myMemberInfoModel.memberInfoChanged(new MemberInfoChange<PsiMember, MemberInfo>(myMemberInfos));
    return myTable;
  }

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

    myTfTargetClassName.getChildComponent().getDocument().addDocumentListener(new com.intellij.openapi.editor.event.DocumentAdapter() {
      public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
        myMemberInfoModel.updateTargetClass();
        validateButtons();
      }
    });

    panel.add(box, BorderLayout.CENTER);
    panel.add(Box.createVerticalStrut(10), BorderLayout.SOUTH);

    validateButtons();
    return panel;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    JTable table = createTable();
    if (table.getRowCount() > 0) {
      table.getSelectionModel().addSelectionInterval(0, 0);
    }
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(table);
    Border titledBorder = IdeBorderFactory.createTitledBorder(RefactoringBundle.message("move.members.members.to.be.moved.border.title"));
    Border emptyBorder = BorderFactory.createEmptyBorder(0, 5, 5, 5);
    Border border = BorderFactory.createCompoundBorder(titledBorder, emptyBorder);
    scrollPane.setBorder(border);
    panel.add(scrollPane, BorderLayout.CENTER);

    myVisibilityPanel = new JavaVisibilityPanel(true, true);
    myVisibilityPanel.setVisibility(null);
    panel.add(myVisibilityPanel, BorderLayout.EAST);

    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myTfTargetClassName.getChildComponent();
  }

  public PsiMember[] getSelectedMembers() {
    final Collection<MemberInfo> selectedMemberInfos = myTable.getSelectedMemberInfos();
    ArrayList<PsiMember> list = new ArrayList<PsiMember>();
    for (MemberInfo selectedMemberInfo : selectedMemberInfos) {
      list.add(selectedMemberInfo.getMember());
    }
    return list.toArray(new PsiMember[list.size()]);
  }

  public String getTargetClassName() {
    return myTfTargetClassName.getText();
  }

  protected void doAction() {
    String message = validateInputData();

    if (message != null) {
      if (message.length() != 0) {
        CommonRefactoringUtil.showErrorMessage(
                MoveMembersImpl.REFACTORING_NAME,
                message,
                HelpID.MOVE_MEMBERS,
                myProject);
      }
      return;
    }

    invokeRefactoring(new MoveMembersProcessor(getProject(), myMoveCallback, new MoveMembersOptions() {
      public String getMemberVisibility() {
        return MoveMembersDialog.this.getMemberVisibility();
      }

      public boolean makeEnumConstant() {
        return MoveMembersDialog.this.makeEnumConstant();
      }

      public PsiMember[] getSelectedMembers() {
        return MoveMembersDialog.this.getSelectedMembers();
      }

      public String getTargetClassName() {
        return MoveMembersDialog.this.getTargetClassName();
      }
    }));

    JavaRefactoringSettings.getInstance().MOVE_PREVIEW_USAGES = isPreviewUsages();
  }

  @Override
  protected void canRun() throws ConfigurationException {
    if (getTargetClassName().length() == 0) throw new ConfigurationException("Destination class name not found");
  }

  private String validateInputData() {
    final PsiManager manager = PsiManager.getInstance(myProject);
    final String fqName = getTargetClassName();
    if ("".equals(fqName)) {
      return RefactoringBundle.message("no.destination.class.specified");
    }
    else {
      if (!JavaPsiFacade.getInstance(manager.getProject()).getNameHelper().isQualifiedName(fqName)) {
        return RefactoringBundle.message("0.is.not.a.legal.fq.name", fqName);
      }
      else {
        RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, fqName);
        final PsiClass[] targetClass = new PsiClass[]{null};
        CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
          public void run() {
            try {
              targetClass[0] = findOrCreateTargetClass(manager, fqName);
            }
            catch (IncorrectOperationException e) {
              CommonRefactoringUtil.showErrorMessage(
                MoveMembersImpl.REFACTORING_NAME,
                e.getMessage(),
                HelpID.MOVE_MEMBERS,
                myProject);
            }
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
              return RefactoringBundle.message("cannot.move.inner.class.0.into.itself", info.getDisplayName());
            }
          }

          if (!targetClass[0].isWritable()) {
            if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, targetClass[0])) return "";
            return "";
          }

          return null;
        }
      }
    }
  }

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


    PsiClass aClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(fqName, GlobalSearchScope.projectScope(myProject));
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
            MoveMembersImpl.REFACTORING_NAME,
            Messages.getQuestionIcon()
    );
    if (answer != 0) return null;
    final Ref<IncorrectOperationException> eRef = new Ref<IncorrectOperationException>();
    final PsiClass newClass = ApplicationManager.getApplication().runWriteAction(new Computable<PsiClass>() {
          public PsiClass compute() {
            try {
              return JavaDirectoryService.getInstance().createClass(directory, className);
            }
            catch (IncorrectOperationException e) {
              eRef.set(e);
              return null;
            }
          }
        });
    if (!eRef.isNull()) throw eRef.get();
    return newClass;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.MOVE_MEMBERS);
  }

  private class ChooseClassAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createWithInnerClassesScopeChooser(
        RefactoringBundle.message("choose.destination.class"), GlobalSearchScope.projectScope(myProject), new TreeClassChooser.ClassFilter() {
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
      PsiClass aClass = chooser.getSelectedClass();
      if (aClass != null) {
        myTfTargetClassName.setText(aClass.getQualifiedName());
        myMemberInfoModel.updateTargetClass();
      }
    }
  }

  private class MyMemberInfoModel extends UsesAndInterfacesDependencyMemberInfoModel {
    PsiClass myTargetClass = null;
    public MyMemberInfoModel() {
      super(mySourceClass, null, false, DEFAULT_CONTAINMENT_VERIFIER);
    }

    public Boolean isFixedAbstract(MemberInfo member) {
      return null;
    }

    public boolean isCheckedWhenDisabled(MemberInfo member) {
      return false;
    }

    public boolean isMemberEnabled(MemberInfo member) {
      if(myTargetClass != null && myTargetClass.isInterface()) {
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
