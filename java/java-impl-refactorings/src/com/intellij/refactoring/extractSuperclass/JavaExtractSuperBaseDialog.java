// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractSuperclass;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.ui.EditorComboBox;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author dsl
 */
public abstract class JavaExtractSuperBaseDialog extends ExtractSuperBaseDialog<PsiClass, MemberInfo> {
  private static final String DESTINATION_PACKAGE_RECENT_KEY = "ExtractSuperBase.RECENT_KEYS";
  protected final DestinationFolderComboBox myDestinationFolderComboBox;


  public JavaExtractSuperBaseDialog(Project project, PsiClass sourceClass, List<MemberInfo> members, @NlsContexts.DialogTitle String refactoringName) {
    super(project, sourceClass, members, refactoringName);
    myDestinationFolderComboBox = new DestinationFolderComboBox() {
      @Override
      public String getTargetPackage() {
        return getTargetPackageName();
      }
    };
  }

  @Override
  protected ComponentWithBrowseButton<EditorComboBox> createPackageNameField() {
    String name = "";
    PsiFile file = mySourceClass.getContainingFile();
    if (file instanceof PsiJavaFile) {
      name = ((PsiJavaFile)file).getPackageName();
    }
    return new PackageNameReferenceEditorCombo(name, myProject, DESTINATION_PACKAGE_RECENT_KEY,
                                                             RefactoringBundle.message("choose.destination.package"));
  }

  @Override
  protected JPanel createDestinationRootPanel() {
    final List<VirtualFile> sourceRoots = JavaProjectRootsUtil.getSuitableDestinationSourceRoots(myProject);
    if (sourceRoots.size() <= 1) return super.createDestinationRootPanel();
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
    final JBLabel label = new JBLabel(RefactoringBundle.message("target.destination.folder"));
    panel.add(label, BorderLayout.NORTH);
    label.setLabelFor(myDestinationFolderComboBox);
    myDestinationFolderComboBox.setData(myProject, myTargetDirectory, ((PackageNameReferenceEditorCombo)myPackageNameField).getChildComponent());
    panel.add(myDestinationFolderComboBox, BorderLayout.CENTER);
    return panel;
  }

  @Override
  protected String getTargetPackageName() {
    return ((PackageNameReferenceEditorCombo)myPackageNameField).getText().trim();
  }

  @Override
  protected JTextField createSourceClassField() {
    JTextField result = new JTextField();
    result.setEditable(false);
    final String qualifiedName = mySourceClass.getQualifiedName();
    result.setText(qualifiedName != null ? qualifiedName : SymbolPresentationUtil.getSymbolPresentableText(mySourceClass));
    return result;
  }

  @Override
  protected JTextField createExtractedSuperNameField() {
    final JTextField superNameField = super.createExtractedSuperNameField();
    superNameField.setText(mySourceClass.getName());
    superNameField.selectAll();
    return superNameField;
  }

  private PsiDirectory getDirUnderSameSourceRoot(final PsiDirectory[] directories) {
    final VirtualFile sourceFile = mySourceClass.getContainingFile().getVirtualFile();
    if (sourceFile != null) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      final VirtualFile sourceRoot = fileIndex.getSourceRootForFile(sourceFile);
      if (sourceRoot != null) {
        for (PsiDirectory dir : directories) {
          if (Comparing.equal(fileIndex.getSourceRootForFile(dir.getVirtualFile()), sourceRoot)) {
            return dir;
          }
        }
      }
    }
    return directories[0];
  }

  @Override
  protected boolean isPossibleToRenameOriginal() {
    //disable for anonymous classes
    return mySourceClass.getNameIdentifier() != null;
  }

  @Override
  protected void preparePackage() throws OperationFailedException {
    final String targetPackageName = getTargetPackageName();
    final PsiFile containingFile = mySourceClass.getContainingFile();
    final boolean fromDefaultPackage = containingFile instanceof PsiClassOwner && ((PsiClassOwner)containingFile).getPackageName().isEmpty();
    if (!(fromDefaultPackage && StringUtil.isEmpty(targetPackageName)) && !PsiNameHelper.getInstance(myProject).isQualifiedName(targetPackageName)) {
      throw new OperationFailedException(JavaRefactoringBundle.message("invalid.package.name", targetPackageName));
    }
    final PsiPackage aPackage = JavaPsiFacade.getInstance(myProject).findPackage(targetPackageName);
    if (aPackage != null) {
      final PsiDirectory[] directories = aPackage.getDirectories(mySourceClass.getResolveScope());
      if (directories.length >= 1) {
        myTargetDirectory = getDirUnderSameSourceRoot(directories);
      }
    }

    final MoveDestination moveDestination =
      myDestinationFolderComboBox.selectDirectory(new PackageWrapper(PsiManager.getInstance(myProject), targetPackageName), false);
    if (moveDestination == null) return;

    myTargetDirectory = myTargetDirectory != null ? WriteAction
      .compute(() -> moveDestination.getTargetDirectory(myTargetDirectory)) : null;

    if (myTargetDirectory == null) {
      throw new OperationFailedException(""); // message already reported by PackageUtil
    }
    String error = RefactoringMessageUtil.checkCanCreateClass(myTargetDirectory, getExtractedSuperName());
    if (error != null) {
      throw new OperationFailedException(error);
    }
  }

  @Override
  protected String getDestinationPackageRecentKey() {
    return DESTINATION_PACKAGE_RECENT_KEY;
  }

  @Nullable
  @Override
  protected String validateName(String name) {
    return PsiNameHelper.getInstance(myProject).isIdentifier(name)
           ? null
           : RefactoringMessageUtil.getIncorrectIdentifierMessage(name);
  }

  @Nullable
  @Override
  protected String validateQualifiedName(String packageName, @NotNull String extractedSuperName) {
    if (StringUtil.getQualifiedName(packageName, extractedSuperName).equals(mySourceClass.getQualifiedName())) {
      return JavaRefactoringBundle.message("different.name.expected");
    }
    return null;
  }
}
