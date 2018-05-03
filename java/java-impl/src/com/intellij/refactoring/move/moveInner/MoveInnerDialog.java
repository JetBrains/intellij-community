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

package com.intellij.refactoring.move.moveInner;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveDialogBase;
import com.intellij.refactoring.move.MoveInstanceMembersUtil;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.RecentsManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MoveInnerDialog extends MoveDialogBase {
  private final Project myProject;
  private final PsiClass myInnerClass;
  private final PsiElement myTargetContainer;
  private final MoveInnerProcessor myProcessor;

  private EditorTextField myClassNameField;
  private NameSuggestionsField myParameterField;
  private JCheckBox myCbPassOuterClass;
  private JPanel myPanel;
  private JCheckBox myCbSearchInComments;
  private JCheckBox myCbSearchForTextOccurences;
  private PackageNameReferenceEditorCombo myPackageNameField;
  private JLabel myPackageNameLabel;
  private JLabel myClassNameLabel;
  private JLabel myParameterNameLabel;
  private JPanel myOpenInEditorPanel;
  private SuggestedNameInfo mySuggestedNameInfo;
  private final PsiClass myOuterClass;

  @NonNls private static final String RECENTS_KEY = "MoveInnerDialog.RECENTS_KEY";

  @Override
  protected String getMovePropertySuffix() {
    return "Inner";
  }

  @Override
  protected String getCbTitle() {
    return "Open moved member in editor";
  }

  public MoveInnerDialog(Project project, PsiClass innerClass, MoveInnerProcessor processor, final PsiElement targetContainer) {
    super(project, true);
    myProject = project;
    myInnerClass = innerClass;
    myTargetContainer = targetContainer;
    myOuterClass = myInnerClass.getContainingClass();
    myProcessor = processor;
    setTitle(MoveInnerImpl.REFACTORING_NAME);
    init();
    myPackageNameLabel.setLabelFor(myPackageNameField.getChildComponent());
    myClassNameLabel.setLabelFor(myClassNameField);
    myParameterNameLabel.setLabelFor(myParameterField);
    myOpenInEditorPanel.add(initOpenInEditorCb(), BorderLayout.EAST);
  }

  public boolean isSearchInComments() {
    return myCbSearchInComments.isSelected();
  }

  public boolean isSearchInNonJavaFiles() {
    return myCbSearchForTextOccurences.isSelected();
  }

  @NotNull
  public String getClassName() {
    return myClassNameField.getText().trim();
  }

  @Nullable
  public String getParameterName() {
    if (myParameterField != null) {
      return myParameterField.getEnteredName();
    }
    else {
      return null;
    }
  }

  public boolean isPassOuterClass() {
    return myCbPassOuterClass.isSelected();
  }

  @NotNull
  public PsiClass getInnerClass() {
    return myInnerClass;
  }

  protected void init() {
    myClassNameField.setText(myInnerClass.getName());
    myClassNameField.selectAll();

    if (!myInnerClass.hasModifierProperty(PsiModifier.STATIC)) {
      myCbPassOuterClass.setSelected(true);
      myCbPassOuterClass.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          myParameterField.setEnabled(myCbPassOuterClass.isSelected());
        }
      });
    }
    else {
      myCbPassOuterClass.setSelected(false);
      myCbPassOuterClass.setEnabled(false);
      myParameterField.setEnabled(false);
    }

    if (myCbPassOuterClass.isEnabled()) {
      boolean thisNeeded = isThisNeeded(myInnerClass, myOuterClass);
      myCbPassOuterClass.setSelected(thisNeeded);
      myParameterField.setEnabled(thisNeeded);
    }

    myCbPassOuterClass.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        boolean selected = myCbPassOuterClass.isSelected();
        myParameterField.getComponent().setEnabled(selected);
      }
    });

    if (!(myTargetContainer instanceof PsiDirectory)) {
      myPackageNameField.setVisible(false);
      myPackageNameLabel.setVisible(false);
    }

    super.init();
  }

  public static boolean isThisNeeded(final PsiClass innerClass, final PsiClass outerClass) {
    final Map<PsiClass, Set<PsiMember>> classesToMembers = MoveInstanceMembersUtil.getThisClassesToMembers(innerClass);
    for (PsiClass psiClass : classesToMembers.keySet()) {
      if (InheritanceUtil.isInheritorOrSelf(outerClass, psiClass, true)) {
        return true;
      }
    }
    return false;
  }

  public JComponent getPreferredFocusedComponent() {
    return myClassNameField;
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.move.moveInner.MoveInnerDialog";
  }

  protected JComponent createNorthPanel() {
    return myPanel;
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  @Nullable
  private PsiElement getTargetContainer() {
    if (myTargetContainer instanceof PsiDirectory) {
      final PsiDirectory psiDirectory = (PsiDirectory)myTargetContainer;
      PsiPackage oldPackage = getTargetPackage();
      String name = oldPackage == null ? "" : oldPackage.getQualifiedName();
      final String targetName = getPackageName();
      if (!Comparing.equal(name, targetName)) {
        final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(myProject);
        final List<VirtualFile> contentSourceRoots = JavaProjectRootsUtil.getSuitableDestinationSourceRoots(myProject);
        final PackageWrapper newPackage = new PackageWrapper(PsiManager.getInstance(myProject), targetName);
        final VirtualFile targetSourceRoot;
        if (contentSourceRoots.size() > 1) {
          PsiPackage targetPackage = JavaPsiFacade.getInstance(myProject).findPackage(targetName);
          PsiDirectory initialDir = null;
          if (targetPackage != null) {
            final PsiDirectory[] directories = targetPackage.getDirectories();
            final VirtualFile root = projectRootManager.getFileIndex().getSourceRootForFile(psiDirectory.getVirtualFile());
            for(PsiDirectory dir: directories) {
              if (Comparing.equal(projectRootManager.getFileIndex().getSourceRootForFile(dir.getVirtualFile()), root)) {
                initialDir = dir;
                break;
              }
            }
          }
          final VirtualFile sourceRoot = MoveClassesOrPackagesUtil.chooseSourceRoot(newPackage, contentSourceRoots, initialDir);
          if (sourceRoot == null) return null;
          targetSourceRoot = sourceRoot;
        }
        else {
          targetSourceRoot = contentSourceRoots.get(0);
        }
        PsiDirectory dir = RefactoringUtil.findPackageDirectoryInSourceRoot(newPackage, targetSourceRoot);
        if (dir == null) {
          dir = ApplicationManager.getApplication().runWriteAction((NullableComputable<PsiDirectory>)() -> {
            try {
              return RefactoringUtil.createPackageDirectoryInSourceRoot(newPackage, targetSourceRoot);
            }
            catch (IncorrectOperationException e) {
              return null;
            }
          });
        }
        return dir;
      }
    }
    return myTargetContainer;
  }

  protected void doAction() {
    String message = null;
    final String className = getClassName();
    final String parameterName = getParameterName();
    PsiManager manager = PsiManager.getInstance(myProject);
    if (className.isEmpty()) {
      message = RefactoringBundle.message("no.class.name.specified");
    }
    else {
      if (!PsiNameHelper.getInstance(manager.getProject()).isIdentifier(className)) {
        message = RefactoringMessageUtil.getIncorrectIdentifierMessage(className);
      }
      else {
        if (myCbPassOuterClass.isSelected()) {
          if (parameterName != null && parameterName.isEmpty()) {
            message = RefactoringBundle.message("no.parameter.name.specified");
          }
          else {
            if (!PsiNameHelper.getInstance(manager.getProject()).isIdentifier(parameterName)) {
              message = RefactoringMessageUtil.getIncorrectIdentifierMessage(parameterName);
            }
          }
        }
        if (message == null) {
          if (myTargetContainer instanceof PsiClass) {
            PsiClass targetClass = (PsiClass)myTargetContainer;
            PsiClass[] classes = targetClass.getInnerClasses();
            for (PsiClass aClass : classes) {
              if (className.equals(aClass.getName())) {
                message = RefactoringBundle.message("inner.class.exists", className, targetClass.getName());
                break;
              }
            }
          }

        }
      }
    }

    PsiElement target = null;

    if (message == null) {
      JavaRefactoringSettings.getInstance().MOVE_INNER_PREVIEW_USAGES = isPreviewUsages();
      if (myCbPassOuterClass.isSelected() && mySuggestedNameInfo != null) {
        mySuggestedNameInfo.nameChosen(getParameterName());
      }

      target = getTargetContainer();
      if (target == null) return;

      if (target instanceof PsiDirectory) {
        message = RefactoringMessageUtil.checkCanCreateClass((PsiDirectory)target, className);

        if (message == null) {
          final String packageName = getPackageName();
          if (packageName.length() > 0 && !PsiNameHelper.getInstance(myProject).isQualifiedName(packageName)) {
            message = RefactoringMessageUtil.getIncorrectIdentifierMessage(packageName);
          }
        }
      }
    }

    if (message != null) {
      CommonRefactoringUtil.showErrorMessage(
        MoveInnerImpl.REFACTORING_NAME,
        message,
        HelpID.MOVE_INNER_UPPER,
        myProject);
      return;
    }

    RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, getPackageName());
    myProcessor.setup(getInnerClass(), className, isPassOuterClass(), parameterName,
                      isSearchInComments(), isSearchInNonJavaFiles(), target);

    final boolean openInEditor = isOpenInEditor();
    saveOpenInEditorOption();
    myProcessor.setOpenInEditor(openInEditor);
    invokeRefactoring(myProcessor);
  }

  private String getPackageName() {
    return myPackageNameField.getText().trim();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.MOVE_INNER_UPPER);
  }

  private void createUIComponents() {
    if (!myInnerClass.hasModifierProperty(PsiModifier.STATIC)) {
      final PsiManager manager = myInnerClass.getManager();
      PsiType outerType = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createType(myInnerClass.getContainingClass());
      mySuggestedNameInfo =  JavaCodeStyleManager.getInstance(myProject).suggestVariableName(VariableKind.PARAMETER, null, null, outerType);
      String[] variants = mySuggestedNameInfo.names;
      myParameterField = new NameSuggestionsField(variants, myProject);
    }
    else {
      myParameterField = new NameSuggestionsField(new String[]{""}, myProject);
      myParameterField.getComponent().setEnabled(false);
    }

    PsiPackage psiPackage = getTargetPackage();
    myPackageNameField = new PackageNameReferenceEditorCombo(psiPackage != null ? psiPackage.getQualifiedName() : "", myProject, RECENTS_KEY,
                                                             RefactoringBundle.message("choose.destination.package"));
  }

  @Nullable
  private PsiPackage getTargetPackage() {
    if (myTargetContainer instanceof PsiDirectory) {
      final PsiDirectory directory = (PsiDirectory)myTargetContainer;
      return JavaDirectoryService.getInstance().getPackage(directory);
    }
    return null;
  }
}
