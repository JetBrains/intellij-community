// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.find.findUsages.JavaFindUsagesHelper;
import com.intellij.find.findUsages.JavaMethodFindUsagesOptions;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public abstract class MethodThrowsFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  protected final String myThrowsCanonicalText;
  private final String myMethodName;
  private final @PropertyKey(resourceBundle = QuickFixBundle.BUNDLE) String myMessageKey;

  protected MethodThrowsFix(@PropertyKey(resourceBundle = QuickFixBundle.BUNDLE) String key, 
                            @NotNull PsiMethod method, @NotNull PsiClassType exceptionType, boolean showClassName) {
    super(method);
    myMessageKey = key;
    myThrowsCanonicalText = exceptionType.getCanonicalText();
    myMethodName = PsiFormatUtil.formatMethod(method,
                                              PsiSubstitutor.EMPTY,
                                              PsiFormatUtilBase.SHOW_NAME | (showClassName ? PsiFormatUtilBase.SHOW_CONTAINING_CLASS : 0), 0);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    processMethod(project, (PsiMethod)startElement);
  }

  abstract void processMethod(@NotNull Project project, @NotNull PsiMethod method);

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile copyFile) {
    PsiMethod startElement = ObjectUtils.tryCast(getStartElement(), PsiMethod.class);
    if (startElement == null) return IntentionPreviewInfo.EMPTY;
    PsiFile file = startElement.getContainingFile();
    if (copyFile.getOriginalFile() == file) {
      processMethod(project, PsiTreeUtil.findSameElementInCopy(startElement, copyFile));
      return IntentionPreviewInfo.DIFF;
    }
    PsiMethod copy = (PsiMethod)startElement.copy();
    processMethod(project, copy);
    TextRange range = copy.getThrowsList().getTextRangeInParent();
    CodeStyleManager.getInstance(project).reformatRange(copy, range.getStartOffset(), range.getEndOffset(), true);
    String origText = startElement.getText();
    String copyText = copy.getText();
    PsiCodeBlock copyBody = copy.getBody();
    PsiCodeBlock origBody = startElement.getBody();
    if (copyBody != null && origBody != null) {
      origText = origText.substring(0, origBody.getStartOffsetInParent() + 1);
      copyText = copyText.substring(0, copyBody.getStartOffsetInParent() + 1);
    }
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, file.getName(), origText, copyText);
  }

  public static class Add extends MethodThrowsFix {
    public Add(@NotNull PsiMethod method, @NotNull PsiClassType exceptionType, boolean showClassName) {
      super("fix.throws.list.add.exception", method, exceptionType, showClassName);
    }

    @Override
    void processMethod(@NotNull Project project, @NotNull PsiMethod myMethod) {
      PsiJavaCodeReferenceElement[] referenceElements = myMethod.getThrowsList().getReferenceElements();
      boolean alreadyThrows =
        ContainerUtil.exists(referenceElements, referenceElement -> referenceElement.getCanonicalText().equals(myThrowsCanonicalText));
      if (!alreadyThrows) {
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(myMethod.getProject());
        final PsiClassType type = (PsiClassType)factory.createTypeFromText(myThrowsCanonicalText, myMethod);
        PsiJavaCodeReferenceElement ref = factory.createReferenceElementByType(type);
        ref = (PsiJavaCodeReferenceElement)JavaCodeStyleManager.getInstance(project).shortenClassReferences(ref);
        myMethod.getThrowsList().add(ref);
      }
    }
  }

  public static class RemoveFirst extends MethodThrowsFix {
    public RemoveFirst(@NotNull PsiMethod method, @NotNull PsiClassType exceptionType, boolean showClassName) {
      super("fix.throws.list.remove.exception", method, exceptionType, showClassName);
    }

    @Override
    void processMethod(@NotNull Project project, @NotNull PsiMethod method) {
      PsiJavaCodeReferenceElement[] referenceElements = method.getThrowsList().getReferenceElements();
      Arrays.stream(referenceElements).filter(referenceElement -> referenceElement.getCanonicalText().equals(myThrowsCanonicalText)).findFirst().ifPresent(PsiElement::delete);
    }
  }

  public static class Remove extends MethodThrowsFix {
    public Remove(@NotNull PsiMethod method, @NotNull PsiClassType exceptionType, boolean showClassName) {
      super("fix.throws.list.remove.exception", method, exceptionType, showClassName);
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile file,
                       @Nullable Editor editor,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {
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
        }), JavaAnalysisBundle.message("processing.method.usages"), true, project);

        if (breakSourceCode && Messages.showYesNoDialog(project, JavaAnalysisBundle
          .message("exception.removal.will.break.source.code.proceed.anyway"), RefactoringBundle.getCannotRefactorMessage(null), null) == Messages.NO) {
          return;
        }
      }

      WriteAction.run(() -> {
        processMethod(project, method);
      });
    }

    @Override
    void processMethod(@NotNull Project project, @NotNull PsiMethod method) {
      PsiType exceptionType = JavaPsiFacade.getElementFactory(project).createTypeFromText(myThrowsCanonicalText, null);
      for (PsiElement element : extractRefsToRemove(method, exceptionType)) {
        element.delete();
      }
    }

    public static PsiElement[] extractRefsToRemove(PsiMethod method, PsiType exceptionType) {
      List<PsiElement> refs = new SmartList<>();
      PsiJavaCodeReferenceElement[] referenceElements = method.getThrowsList().getReferenceElements();
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(method.getProject());
      Arrays.stream(referenceElements).filter(ref -> {
        PsiType refType = elementFactory.createType(ref);
        return exceptionType.isAssignableFrom(refType);
      }).forEach(refs::add);
      PsiDocComment comment = method.getDocComment();
      if (comment != null) {
        Arrays
          .stream(comment.getTags())
          .filter(tag -> "throws".equals(tag.getName()))
          .filter(tag -> {
            PsiClass tagValueClass = JavaDocUtil.resolveClassInTagValue(tag.getValueElement());
            if (tagValueClass == null) return false;
            PsiClassType tagValueType = elementFactory.createType(tagValueClass);
            return exceptionType.isAssignableFrom(tagValueType);
          })
          .forEach(refs::add);
      }
      return refs.toArray(PsiElement.EMPTY_ARRAY);
    }
  }

  @NotNull
  @Override
  public final String getText() {
    return QuickFixBundle.message(myMessageKey, StringUtil.getShortName(myThrowsCanonicalText), myMethodName);
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
