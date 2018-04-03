// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class MethodThrowsFix extends LocalQuickFixOnPsiElement {
  private static final Logger LOG = Logger.getInstance(MethodThrowsFix.class);

  private final String myThrowsCanonicalText;
  private final boolean myAddThrow;
  private final String myMethodName;

  public MethodThrowsFix(@NotNull PsiMethod method, @NotNull PsiClassType exceptionType, boolean addThrow, boolean showClassName) {
    super(method);
    myThrowsCanonicalText = exceptionType.getCanonicalText();
    myAddThrow = addThrow;
    myMethodName = PsiFormatUtil.formatMethod(method,
                                              PsiSubstitutor.EMPTY,
                                              PsiFormatUtilBase.SHOW_NAME | (showClassName ? PsiFormatUtilBase.SHOW_CONTAINING_CLASS
                                                                                                   : 0),
                                              0);
  }

  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message(myAddThrow ? "fix.throws.list.add.exception" : "fix.throws.list.remove.exception",
                                  StringUtil.getShortName(myThrowsCanonicalText),
                                  myMethodName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.throws.list.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return !(((PsiMethod)startElement).getThrowsList() instanceof PsiCompiledElement); // can happen in Kotlin
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    final PsiMethod myMethod = (PsiMethod)startElement;
    PsiJavaCodeReferenceElement[] referenceElements = myMethod.getThrowsList().getReferenceElements();
    try {
      boolean alreadyThrows = false;
      for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
        if (referenceElement.getCanonicalText().equals(myThrowsCanonicalText)) {
          alreadyThrows = true;
          if (!myAddThrow) {
            referenceElement.delete();
            break;
          }
        }
      }
      if (myAddThrow) {
        if (!alreadyThrows) {
          final PsiElementFactory factory = JavaPsiFacade.getInstance(myMethod.getProject()).getElementFactory();
          final PsiClassType type = (PsiClassType)factory.createTypeFromText(myThrowsCanonicalText, myMethod);
          PsiJavaCodeReferenceElement ref = factory.createReferenceElementByType(type);
          ref = (PsiJavaCodeReferenceElement)JavaCodeStyleManager.getInstance(project).shortenClassReferences(ref);
          myMethod.getThrowsList().add(ref);
        }
      } else {
        PsiDocComment comment = myMethod.getDocComment();
        if (comment != null) {
          Arrays
            .stream(comment.getTags())
            .filter(tag -> "throws".equals(tag.getName()))
            .filter(tag -> {
              PsiClass tagValueClass = JavaDocUtil.resolveClassInTagValue(tag.getValueElement());
              return tagValueClass != null && myThrowsCanonicalText.equals(tagValueClass.getQualifiedName());
            })
            .forEach(tag -> tag.delete());
        }

      }
      UndoUtil.markPsiFileForUndo(file);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
