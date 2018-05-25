// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.find.findUsages.JavaFindUsagesHelper;
import com.intellij.find.findUsages.JavaMethodFindUsagesOptions;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public abstract class MethodThrowsFix extends LocalQuickFixOnPsiElement {
  protected final String myThrowsCanonicalText;
  private final String myMethodName;

  protected MethodThrowsFix(@NotNull PsiMethod method, @NotNull PsiClassType exceptionType, boolean showClassName) {
    super(method);
    myThrowsCanonicalText = exceptionType.getCanonicalText();
    myMethodName = PsiFormatUtil.formatMethod(method,
                                              PsiSubstitutor.EMPTY,
                                              PsiFormatUtilBase.SHOW_NAME | (showClassName ? PsiFormatUtilBase.SHOW_CONTAINING_CLASS : 0), 0);
  }

  public static class Add extends MethodThrowsFix {
    public Add(@NotNull PsiMethod method, @NotNull PsiClassType exceptionType, boolean showClassName) {
      super(method, exceptionType, showClassName);
    }

    @NotNull
    @Override
    protected String getTextMessageKey() {
      return "fix.throws.list.add.exception";
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      final PsiMethod myMethod = (PsiMethod)startElement;
      PsiJavaCodeReferenceElement[] referenceElements = myMethod.getThrowsList().getReferenceElements();
      boolean alreadyThrows = Arrays.stream(referenceElements).anyMatch(referenceElement -> referenceElement.getCanonicalText().equals(myThrowsCanonicalText));
      if (!alreadyThrows) {
        final PsiElementFactory factory = JavaPsiFacade.getInstance(myMethod.getProject()).getElementFactory();
        final PsiClassType type = (PsiClassType)factory.createTypeFromText(myThrowsCanonicalText, myMethod);
        PsiJavaCodeReferenceElement ref = factory.createReferenceElementByType(type);
        ref = (PsiJavaCodeReferenceElement)JavaCodeStyleManager.getInstance(project).shortenClassReferences(ref);
        myMethod.getThrowsList().add(ref);
      }
    }
  }

  public static class Remove extends MethodThrowsFix {
    public Remove(@NotNull PsiMethod method, @NotNull PsiClassType exceptionType, boolean showClassName) {
      super(method, exceptionType, showClassName);
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @NotNull
    @Override
    protected String getTextMessageKey() {
      return "fix.throws.list.remove.exception";
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      if (!ReadonlyStatusHandler.ensureFilesWritable(project, file.getVirtualFile())) {
        return;
      }
      final PsiMethod method = (PsiMethod)startElement;

      PsiClassType exception = JavaPsiFacade.getElementFactory(project).createTypeByFQClassName(myThrowsCanonicalText, method.getResolveScope());
      if (!ExceptionUtil.isUncheckedException(exception)) {
        JavaMethodFindUsagesOptions ops = new JavaMethodFindUsagesOptions(project);
        ops.isSearchForTextOccurrences = false;
        ops.isImplicitToString = false;
        ops.isSkipImportStatements = true;

        boolean breakSourceCode = !ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> JavaFindUsagesHelper.processElementUsages(
          method, ops, usage -> {
          PsiElement element = usage.getElement();
          if (!(element instanceof PsiReferenceExpression)) return true;
          PsiElement parent = element.getParent();
          if (parent instanceof PsiCallExpression) {
            ExceptionUtil.HandlePlace place = ExceptionUtil.getHandlePlace(parent, exception, null);
            if (place instanceof ExceptionUtil.HandlePlace.TryCatch) {
              PsiParameter parameter = ((ExceptionUtil.HandlePlace.TryCatch)place).getParameter();
              PsiCodeBlock block = ((ExceptionUtil.HandlePlace.TryCatch)place).getTryStatement().getTryBlock();
              if (block != null) {
                PsiCallExpression call = (PsiCallExpression)parent;
                Collection<PsiClassType> types = ExceptionUtil.collectUnhandledExceptions(block, null, call);
                if (types.contains(exception)) {
                  return true;
                }
                List<PsiClassType> thrownCheckedExceptions = new ArrayList<>(ExceptionUtil.getThrownCheckedExceptions(call));
                thrownCheckedExceptions.remove(exception);

                PsiType caughtExceptionType = parameter.getType();
                if (Stream.concat(types.stream(), thrownCheckedExceptions.stream()).noneMatch(ex -> caughtExceptionType.isAssignableFrom(ex))) {
                  return false;
                }
              }
            }
          }
          return true;
        }), "Processing Method Usages...", true, project);

        if (breakSourceCode && Messages.showYesNoDialog(project, "Exception removal will break source code. Proceed anyway?", RefactoringBundle.getCannotRefactorMessage(null), null) == Messages.NO) {
          return;
        }
      }
      WriteAction.run(() -> removeException(method));
    }

    private void removeException(PsiMethod myMethod) {
      PsiJavaCodeReferenceElement[] referenceElements = myMethod.getThrowsList().getReferenceElements();
      Arrays.stream(referenceElements).filter(referenceElement -> referenceElement.getCanonicalText().equals(myThrowsCanonicalText)).forEach(PsiElement::delete);
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
  }

  @NotNull
  protected abstract String getTextMessageKey();

  @NotNull
  @Override
  public final String getText() {
    return QuickFixBundle.message(getTextMessageKey(), StringUtil.getShortName(myThrowsCanonicalText), myMethodName);
  }

  @Override
  @NotNull
  public final String getFamilyName() {
    return QuickFixBundle.message("fix.throws.list.family");
  }

  @Override
  public final boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return !(((PsiMethod)startElement).getThrowsList() instanceof PsiCompiledElement); // can happen in Kotlin
  }
}
