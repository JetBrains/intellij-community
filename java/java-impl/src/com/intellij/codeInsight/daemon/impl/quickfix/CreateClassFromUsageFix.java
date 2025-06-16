// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public final class CreateClassFromUsageFix extends CreateClassFromUsageBaseFix {
  public CreateClassFromUsageFix(PsiJavaCodeReferenceElement refElement, CreateClassKind kind) {
    super(kind, refElement);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiJavaCodeReferenceElement element = getRefElement();
    if (element == null) return IntentionPreviewInfo.EMPTY;
    element = PsiTreeUtil.findSameElementInCopy(element, psiFile);
    String superClassName = getSuperClassName(element);
    PsiClass aClass = myKind.create(JavaPsiFacade.getElementFactory(project), element.getReferenceName());
    if (StringUtil.isNotEmpty(superClassName) &&
        (myKind != CreateClassKind.ENUM || !superClassName.equals(CommonClassNames.JAVA_LANG_ENUM)) &&
        (myKind != CreateClassKind.RECORD || !superClassName.equals(CommonClassNames.JAVA_LANG_RECORD))) {
      CreateFromUsageUtils.setupSuperClassReference(aClass, superClassName);
    }
    CreateFromUsageBaseFix.setupGenericParameters(aClass, element);
    PsiDeconstructionPattern pattern = getDeconstructionPattern(element);
    if (pattern != null) {
      CreateInnerClassFromUsageFix.setupRecordFromDeconstructionPattern(aClass, pattern, getText());
    }
    CodeStyleManager.getInstance(project).reformat(aClass);
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, "", aClass.getText());
  }

  @Override
  public String getText(String varName) {
    return CommonQuickFixBundle.message("fix.create.title.x", myKind.getDescriptionAccusative(), varName);
  }


  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    PsiJavaCodeReferenceElement element = getRefElement();
    if (element == null || !FileModificationService.getInstance().preparePsiElementForWrite(element)) {
      return;
    }

    String superClassName = getSuperClassName(element);
    PsiClass aClass = CreateFromUsageUtils.createClass(element, myKind, superClassName);
    if (aClass == null) {
      return;
    }

    ApplicationManager.getApplication().runWriteAction(() -> {
      PsiJavaCodeReferenceElement refElement = element;
      try {
        refElement = (PsiJavaCodeReferenceElement)refElement.bindToElement(aClass);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }

      IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

      PsiDeconstructionPattern pattern = getDeconstructionPattern(element);
      if (pattern != null) {
        CreateInnerClassFromUsageFix.setupRecordFromDeconstructionPattern(aClass, pattern, getText());
      }
      else {
        Navigatable descriptor = PsiNavigationSupport.getInstance()
          .createNavigatable(refElement.getProject(), aClass.getContainingFile().getVirtualFile(), aClass.getTextOffset());
        descriptor.navigate(true);
      }
    });
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
