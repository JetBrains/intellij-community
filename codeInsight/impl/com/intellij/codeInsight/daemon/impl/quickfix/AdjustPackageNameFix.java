package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class AdjustPackageNameFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AdjustPackageNameFix");
  private final PsiJavaFile myFile;
  private PsiPackageStatement myStatement;
  private PsiPackage myTargetPackage;

  public AdjustPackageNameFix(PsiJavaFile file, PsiPackageStatement statement, PsiPackage targetPackage) {
    myFile = file;
    myStatement = statement;
    myTargetPackage = targetPackage;
  }

  @NotNull
  public String getText() {
    String text = QuickFixBundle.message("adjust.package.text", myTargetPackage.getQualifiedName());
    return text;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("adjust.package.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myFile != null
        && myFile.isValid()
        && myFile.getManager().isInProject(myFile)
        && myTargetPackage != null
        && myTargetPackage.isValid()
        && (myStatement == null || myStatement.isValid())
        ;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myFile)) return;

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

  public boolean startInWriteAction() {
    return true;
  }


}
