/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.codeInspection.wrongPackageStatement;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.*;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.jsp.JspFile;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 14-Nov-2005
 */
public class WrongPackageStatementInspection extends LocalInspectionTool {
  @Nullable
  public ProblemDescriptor[] checkFile(PsiFile file, InspectionManager manager, boolean isOnTheFly) {
    // does not work in tests since CodeInsightTestCase copies file into temporary location
    if (ApplicationManager.getApplication().isUnitTestMode()) return null;
    if (file instanceof PsiJavaFile) {
      if (PsiUtil.isInJspFile(file)) return null;
      PsiJavaFile javaFile = (PsiJavaFile)file;
      // highlight the first class in the file only
      PsiClass[] classes = javaFile.getClasses();
      if (classes.length == 0) return null;
      PsiDirectory directory = javaFile.getContainingDirectory();
      if (directory == null) return null;
      PsiPackage dirPackage = directory.getPackage();
      if (dirPackage == null) return null;
      PsiPackageStatement packageStatement = javaFile.getPackageStatement();

      String packageName = dirPackage.getQualifiedName();
      if (!Comparing.strEqual(packageName, "", true) && packageStatement == null) {
        String description = JavaErrorMessages.message("missing.package.statement", packageName);

        return new ProblemDescriptor[]{manager.createProblemDescriptor(classes[0].getNameIdentifier(), description,
                                                                       new AdjustPackageNameFix(javaFile, null, dirPackage),
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING)};
      }


      if (packageStatement != null) {
        final PsiJavaCodeReferenceElement packageReference = packageStatement.getPackageReference();
        PsiPackage classPackage = (PsiPackage)packageReference.resolve();
        List<LocalQuickFix> availableFixes = new ArrayList<LocalQuickFix>();
        if (classPackage == null){
          availableFixes.add(new AdjustPackageNameFix(javaFile, packageStatement, dirPackage));
        } else if (!Comparing.equal(dirPackage.getQualifiedName(), packageReference.getText(), true)){
          availableFixes.add(new AdjustPackageNameFix(javaFile, packageStatement, dirPackage));
          availableFixes.add(new MoveToPackageFix(file, classPackage));
        }
        if (!availableFixes.isEmpty()){
          String description = JavaErrorMessages.message("package.name.file.path.mismatch",
                                                         packageReference.getText(),
                                                         dirPackage.getQualifiedName());
          return new ProblemDescriptor[]{manager.createProblemDescriptor(packageStatement, description, availableFixes.toArray(new LocalQuickFix[availableFixes.size()]), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)};

        }
      }
    }
    return null;
  }

  public String getGroupDisplayName() {
    return "";
  }

  public String getDisplayName() {
    return InspectionsBundle.message("wrong.package.statement");
  }

  @NonNls
  public String getShortName() {
    return "WrongPackageStatement";
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  private static class AdjustPackageNameFix implements LocalQuickFix {
    private final PsiJavaFile myFile;
    private PsiPackageStatement myStatement;
    private PsiPackage myTargetPackage;

    public AdjustPackageNameFix(PsiJavaFile file, PsiPackageStatement statement, PsiPackage targetPackage) {
      myFile = file;
      myStatement = statement;
      myTargetPackage = targetPackage;
    }

    public String getName() {
      return QuickFixBundle.message("adjust.package.text", myTargetPackage.getQualifiedName());
    }

    public String getFamilyName() {
      return QuickFixBundle.message("adjust.package.family");
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
      if (!CodeInsightUtil.prepareFileForWrite(myFile)) return;

      //hack. Need a way to check applicability of the fix
      if (myStatement != null && myStatement.isValid()) return;

      try {
        PsiElementFactory factory = myFile.getManager().getElementFactory();
        if (myTargetPackage.getQualifiedName().length() == 0) {
          if (myStatement != null) {
            myStatement.delete();
          }
        }
        else {
          if (myStatement != null) {
            PsiJavaCodeReferenceElement packageReferenceElement = factory.createPackageReferenceElement(myTargetPackage);
            myStatement.getPackageReference().replace(packageReferenceElement);
          }
          else {
            PsiPackageStatement packageStatement = factory.createPackageStatement(myTargetPackage.getQualifiedName());
            myFile.addAfter(packageStatement, null);
          }
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.MoveToPackageFix");

  private static class MoveToPackageFix implements LocalQuickFix {
    private PsiFile myFile;
    private PsiPackage myTargetPackage;

    public MoveToPackageFix(PsiFile file, PsiPackage targetPackage) {
      myFile = file;
      myTargetPackage = targetPackage;
    }

    public String getName() {
      return QuickFixBundle.message("move.class.to.package.text",
                                    myTargetPackage.getQualifiedName());
    }

    public String getFamilyName() {
      return QuickFixBundle.message("move.class.to.package.family");
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
      if (!CodeInsightUtil.prepareFileForWrite(myFile)) return;

      try {
        String packageName = myTargetPackage.getQualifiedName();
        PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(project, packageName, null, true);

        if (directory == null) {
          return;
        }
        String error = RefactoringMessageUtil.checkCanCreateFile(directory, myFile.getName());
        if (error != null) {
          Messages.showMessageDialog(project, error, CommonBundle.getErrorTitle(), Messages.getErrorIcon());
          return;
        }
        new MoveClassesOrPackagesProcessor(
          project,
          new PsiElement[]{((PsiJavaFile)myFile).getClasses()[0]},
          new SingleSourceRootMoveDestination(PackageWrapper.create(directory.getPackage()), directory), false,
          false,
          null).run();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }


  }
}
