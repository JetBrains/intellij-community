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
package com.intellij.refactoring.extractSuperclass;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pass;
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


  public JavaExtractSuperBaseDialog(Project project, PsiClass sourceClass, List<MemberInfo> members, String refactoringName) {
    super(project, sourceClass, members, refactoringName);
    myDestinationFolderComboBox = new DestinationFolderComboBox() {
      @Override
      public String getTargetPackage() {
        return getTargetPackageName();
      }
    };
  }

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
    myDestinationFolderComboBox.setData(myProject, myTargetDirectory, new Pass<String>() {
      @Override
      public void pass(String s) {
      }
    }, ((PackageNameReferenceEditorCombo)myPackageNameField).getChildComponent());
    panel.add(myDestinationFolderComboBox, BorderLayout.CENTER);
    return panel;
  }

  @Override
  protected String getTargetPackageName() {
    return ((PackageNameReferenceEditorCombo)myPackageNameField).getText().trim();
  }

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
  protected void preparePackage() throws OperationFailedException {
    final String targetPackageName = getTargetPackageName();
    final PsiFile containingFile = mySourceClass.getContainingFile();
    final boolean fromDefaultPackage = containingFile instanceof PsiClassOwner && ((PsiClassOwner)containingFile).getPackageName().isEmpty(); 
    if (!(fromDefaultPackage && StringUtil.isEmpty(targetPackageName)) && !PsiNameHelper.getInstance(myProject).isQualifiedName(targetPackageName)) {
      throw new OperationFailedException("Invalid package name: " + targetPackageName);
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

    myTargetDirectory = myTargetDirectory != null ? ApplicationManager.getApplication().runWriteAction(new Computable<PsiDirectory>() {
      @Override
      public PsiDirectory compute() {
        return moveDestination.getTargetDirectory(myTargetDirectory);
      }
    }) : null;

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
           ? name.equals(mySourceClass.getName())
             ? "Different name expected" : null
           : RefactoringMessageUtil.getIncorrectIdentifierMessage(name);
  }
}
