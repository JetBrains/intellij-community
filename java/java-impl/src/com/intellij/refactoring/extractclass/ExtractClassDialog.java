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
package com.intellij.refactoring.extractclass;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.classMembers.DelegatingMemberInfoModel;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.classMembers.MemberInfoChange;
import com.intellij.refactoring.classMembers.MemberInfoChangeListener;
import com.intellij.refactoring.ui.*;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

@SuppressWarnings({"OverridableMethodCallInConstructor"})
class ExtractClassDialog extends RefactoringDialog implements MemberInfoChangeListener<PsiMember, MemberInfo> {
  private final Map<MemberInfoBase<PsiMember>, PsiMember> myMember2CauseMap = new HashMap<MemberInfoBase<PsiMember>, PsiMember>();
  private final PsiClass sourceClass;
  private final List<MemberInfo> memberInfo;
  private final JTextField classNameField;
  private final ReferenceEditorComboWithBrowseButton packageTextField;
  private final JTextField sourceClassTextField;
  private JCheckBox myGenerateAccessorsCb;
  private final JavaVisibilityPanel myVisibilityPanel;
  private JCheckBox extractAsEnum;
  private List<MemberInfo> enumConstants = new ArrayList<MemberInfo>();

  ExtractClassDialog(PsiClass sourceClass, PsiMember selectedMember) {
    super(sourceClass.getProject(), true);
    setModal(true);
    setTitle(RefactorJBundle.message("extract.class.title"));
    myVisibilityPanel = new JavaVisibilityPanel(true, true);
    myVisibilityPanel.setVisibility(null);
    this.sourceClass = sourceClass;
    final DocumentListener docListener = new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        validateButtons();
      }
    };
    classNameField = new JTextField();
    final PsiFile file = sourceClass.getContainingFile();
    final String text = file instanceof PsiJavaFile ? ((PsiJavaFile)file).getPackageName() : "";
    packageTextField = new PackageNameReferenceEditorCombo(text, myProject, "ExtractClass.RECENTS_KEY", RefactorJBundle.message("choose.destination.package.label"));
    packageTextField.getChildComponent().getDocument().addDocumentListener(new com.intellij.openapi.editor.event.DocumentAdapter() {
      @Override
      public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
        validateButtons();
      }
    });
    classNameField.getDocument().addDocumentListener(docListener);
    sourceClassTextField = new JTextField();
    final MemberInfo.Filter<PsiMember> filter = new MemberInfo.Filter<PsiMember>() {
      public boolean includeMember(PsiMember element) {
        if (element instanceof PsiMethod) {
          return !((PsiMethod)element).isConstructor() && ((PsiMethod)element).getBody() != null;
        }
        else if (element instanceof PsiField) {
          return true;
        }
        else if (element instanceof PsiClass) {
          return PsiTreeUtil.isAncestor(ExtractClassDialog.this.sourceClass, element, true);
        }
        return false;
      }
    };
    memberInfo = MemberInfo.extractClassMembers(this.sourceClass, filter, false);
    extractAsEnum = new JCheckBox("Extract as enum");
    boolean hasConstants = false;
    for (MemberInfo info : memberInfo) {
      final PsiMember member = info.getMember();
      if (member.equals(selectedMember)) {
        info.setChecked(true);
      }
      if (!hasConstants && member instanceof PsiField && member.hasModifierProperty(PsiModifier.FINAL) && member.hasModifierProperty(PsiModifier.STATIC)) {
        hasConstants = true;
      }
    }
    if (!hasConstants) {
      extractAsEnum.setVisible(false);
    }
    super.init();
    sourceClassTextField.setText(sourceClass.getQualifiedName());
    validateButtons();
  }

  protected void doAction() {

    final List<PsiField> fields = getFieldsToExtract();
    final List<PsiMethod> methods = getMethodsToExtract();
    final List<PsiClass> classes = getClassesToExtract();
    final String newClassName = getClassName();
    final String packageName = getPackageName();

    Collections.sort(enumConstants, new Comparator<MemberInfo>() {
      public int compare(MemberInfo o1, MemberInfo o2) {
        return o1.getMember().getTextOffset() - o2.getMember().getTextOffset();
      }
    });
    final ExtractClassProcessor processor = new ExtractClassProcessor(sourceClass, fields, methods, classes, packageName, newClassName, myVisibilityPanel.getVisibility(), isGenerateAccessors(),
                                                                      isExtractAsEnum() ? enumConstants : Collections.<MemberInfo>emptyList());
    if (processor.getCreatedClass() == null) return;
    invokeRefactoring(processor);
  }

  @Override
  protected void canRun() throws ConfigurationException {
    final Project project = sourceClass.getProject();
    final PsiNameHelper nameHelper = JavaPsiFacade.getInstance(project).getNameHelper();
    final List<PsiMethod> methods = getMethodsToExtract();
    final List<PsiField> fields = getFieldsToExtract();
    final List<PsiClass> innerClasses = getClassesToExtract();
    if (methods.isEmpty() && fields.isEmpty() && innerClasses.isEmpty()) {
      throw new ConfigurationException("Nothing found to extract");
    }

    final String className = getClassName();
    if (className.length() == 0 || !nameHelper.isIdentifier(className)) {
      throw new ConfigurationException("\'" + StringUtil.first(className, 10, true) + "\' is invalid extracted class name");
    }

    final String packageName = getPackageName();
    if (packageName.length() == 0 || !nameHelper.isQualifiedName(packageName)){
      throw new ConfigurationException("\'" + StringUtil.last(packageName, 10, true) + "\' is invalid extracted class package name");
    }
  }

  @NotNull
  public String getPackageName() {
    return packageTextField.getText().trim();
  }

  @NotNull
  public String getClassName() {
    return classNameField.getText().trim();
  }

  public List<PsiField> getFieldsToExtract() {
    return getMembersToExtract(true, PsiField.class);
  }

  public <T> List<T> getMembersToExtract(final boolean checked, Class<T> memberClass) {
    final List<T> out = new ArrayList<T>();
    for (MemberInfo info : memberInfo) {
      if (checked && !info.isChecked()) continue;
      if (!checked && info.isChecked()) continue;
      final PsiMember member = info.getMember();
      if (memberClass.isAssignableFrom(member.getClass())) {
        out.add((T)member);
      }
    }
    return out;
  }

  public List<PsiMethod> getMethodsToExtract() {
    return getMembersToExtract(true, PsiMethod.class);
  }

  public List<PsiClass> getClassesToExtract() {
    return getMembersToExtract(true, PsiClass.class);
  }

  public List<PsiClassInitializer> getClassInitializersToExtract() {
    return getMembersToExtract(true, PsiClassInitializer.class);
  }

  public boolean isGenerateAccessors() {
    return myGenerateAccessorsCb.isSelected();
  }

  public boolean isExtractAsEnum() {
    return extractAsEnum.isVisible() && extractAsEnum.isEnabled() && extractAsEnum.isSelected();
  }

  protected String getDimensionServiceKey() {
    return "RefactorJ.ExtractClass";
  }

  protected JComponent createNorthPanel() {
    final Box box = Box.createVerticalBox();

    sourceClassTextField.setEditable(false);
    final JPanel sourceClassPanel = new JPanel(new BorderLayout());
    sourceClassPanel.add(new JLabel(RefactorJBundle.message("extract.class.from.label")), BorderLayout.NORTH);
    sourceClassPanel.add(sourceClassTextField, BorderLayout.CENTER);
    box.add(sourceClassPanel);

    box.add(Box.createVerticalStrut(10));
    final JLabel classNameLabel = new JLabel(RefactorJBundle.message("name.for.new.class.label"));
    classNameLabel.setLabelFor(classNameField);
    classNameLabel.setDisplayedMnemonic('N');
    final JPanel classNamePanel = new JPanel(new BorderLayout());
    classNamePanel.add(classNameLabel, BorderLayout.NORTH);
    classNamePanel.add(classNameField, BorderLayout.CENTER);
    classNamePanel.add(extractAsEnum, BorderLayout.SOUTH);
    box.add(classNamePanel);

    box.add(Box.createVerticalStrut(5));

    final JPanel packageNamePanel = new JPanel(new BorderLayout());
    final JLabel packageLabel = new JLabel(RefactorJBundle.message("package.for.new.class.label"));
    packageLabel.setLabelFor(packageTextField);
    packageLabel.setDisplayedMnemonic('P');
    packageNamePanel.add(packageLabel, BorderLayout.NORTH);
    packageNamePanel.add(packageTextField, BorderLayout.CENTER);
    box.add(packageNamePanel);

    box.add(Box.createVerticalStrut(10));
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(box, BorderLayout.CENTER);
    return panel;
  }

  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final MemberSelectionPanel memberSelectionPanel =
      new MemberSelectionPanel(RefactorJBundle.message("members.to.extract.label"), memberInfo, "As enum"){
        @Override
        protected MemberSelectionTable createMemberSelectionTable(final List<MemberInfo> memberInfo, String abstractColumnHeader) {
          return new MemberSelectionTable(memberInfo, abstractColumnHeader) {
            @Nullable
            @Override
            protected Object getAbstractColumnValue(MemberInfo memberInfo) {
              if (isExtractAsEnum()) {
                final PsiMember member = memberInfo.getMember();
                if (isConstantField(member)) {
                  return Boolean.valueOf(enumConstants.contains(memberInfo));
                }
              }
              return null;
            }

            @Override
            protected boolean isAbstractColumnEditable(int rowIndex) {
              final MemberInfo info = memberInfo.get(rowIndex);
              if (info.isChecked()) {
                final PsiMember member = info.getMember();
                if (isConstantField(member)) {
                  if (enumConstants.isEmpty()) return true;
                  final MemberInfo currentEnumConstant = enumConstants.get(0);
                  if (((PsiField)currentEnumConstant.getMember()).getType().equals(((PsiField)member).getType())) return true;
                }
              }
              return false;
            }
          };
        }
      };
    final MemberSelectionTable table = memberSelectionPanel.getTable();
    table.setMemberInfoModel(new DelegatingMemberInfoModel<PsiMember, MemberInfo>(table.getMemberInfoModel()) {

      @Override
      public int checkForProblems(@NotNull final MemberInfo member) {
        final PsiMember cause = getCause(member);
        if (member.isChecked() && cause != null) return ERROR;
        if (!member.isChecked() && cause != null) return WARNING;
        return OK;
      }

      @Override
      public String getTooltipText(final MemberInfo member) {
        final PsiMember cause = getCause(member);
        if (cause != null) {
          final String presentation = SymbolPresentationUtil.getSymbolPresentableText(cause);
          if (member.isChecked()) {
            return "Depends on " + presentation + " from " + sourceClass.getName();
          } else {
            final String className = getClassName();
            return "Depends on " + presentation + " from new class" + (className.length() > 0 ? ": " + className : "");
          }
        }
        return null;
      }

      private PsiMember getCause(final MemberInfo member) {
        PsiMember cause = myMember2CauseMap.get(member);

        if (cause != null) return cause;

        final BackpointerUsageVisitor visitor;
        if (member.isChecked()) {
          visitor = new BackpointerUsageVisitor(getFieldsToExtract(), getClassesToExtract(), getMethodsToExtract(), sourceClass);
        }
        else {
          visitor =
            new BackpointerUsageVisitor(getMembersToExtract(false, PsiField.class), getMembersToExtract(false, PsiClass.class),
                                        getMembersToExtract(false, PsiMethod.class), sourceClass, false);
        }

        member.getMember().accept(visitor);
        cause = visitor.getCause();
        myMember2CauseMap.put(member, cause);
        return cause;
      }
    });
    panel.add(memberSelectionPanel, BorderLayout.CENTER);
    table.addMemberInfoChangeListener(this);
    extractAsEnum.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        table.repaint();
      }
    });
    myGenerateAccessorsCb = new JCheckBox("Generate accessors");
    myGenerateAccessorsCb.setMnemonic('G');
    panel.add(myGenerateAccessorsCb, BorderLayout.SOUTH);

    panel.add(myVisibilityPanel, BorderLayout.EAST);
    return panel;
  }

  private static boolean isConstantField(PsiMember member) {
    return member instanceof PsiField &&
           member.hasModifierProperty(PsiModifier.STATIC) &&
          // member.hasModifierProperty(PsiModifier.FINAL) &&
           ((PsiField)member).hasInitializer();
  }

  public JComponent getPreferredFocusedComponent() {
    return classNameField;
  }

  protected void doHelpAction() {
    final HelpManager helpManager = HelpManager.getInstance();
    helpManager.invokeHelp(HelpID.ExtractClass);
  }

  public void memberInfoChanged(MemberInfoChange memberInfoChange) {
    validateButtons();
    myMember2CauseMap.clear();
    if (extractAsEnum.isVisible()) {
      for (Object info : memberInfoChange.getChangedMembers()) {
        if (((MemberInfo)info).isToAbstract()) {
          enumConstants.add((MemberInfo)info);
        } else {
          enumConstants.remove((MemberInfo)info);
        }
      }
      extractAsEnum.setEnabled(canExtractEnum());
    }
  }

  private boolean canExtractEnum() {
    final List<PsiField> fields = new ArrayList<PsiField>();
    final List<PsiClass> innerClasses = new ArrayList<PsiClass>();
    final List<PsiMethod> methods = new ArrayList<PsiMethod>();
    for (MemberInfo info : memberInfo) {
      if (info.isChecked()) {
        final PsiMember member = info.getMember();
        if (member instanceof PsiField) {
          fields.add((PsiField)member);
        } else if (member instanceof PsiMethod) {
          methods.add((PsiMethod)member);
        } else if (member instanceof PsiClass) {
          innerClasses.add((PsiClass)member);
        }
      }
    }
    return !new BackpointerUsageVisitor(fields, innerClasses, methods, sourceClass).backpointerRequired();
  }
}
