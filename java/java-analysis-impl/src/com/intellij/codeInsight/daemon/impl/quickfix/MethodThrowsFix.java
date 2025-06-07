// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.find.findUsages.JavaFindUsagesHelper;
import com.intellij.find.findUsages.JavaMethodFindUsagesOptions;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.*;
import java.util.stream.Stream;

public abstract class MethodThrowsFix extends PsiBasedModCommandAction<PsiMethod> {
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

  public static class Add extends MethodThrowsFix {
    public Add(@NotNull PsiMethod method, @NotNull PsiClassType exceptionType, boolean showClassName) {
      super("fix.throws.list.add.exception", method, exceptionType, showClassName);
    }

    @Override
    protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiMethod myMethod) {
      PsiJavaCodeReferenceElement[] referenceElements = myMethod.getThrowsList().getReferenceElements();
      boolean alreadyThrows =
        ContainerUtil.exists(referenceElements, referenceElement -> referenceElement.getCanonicalText().equals(myThrowsCanonicalText));
      if (alreadyThrows) return ModCommand.nop();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(myMethod.getProject());
      final PsiClassType type = (PsiClassType)factory.createTypeFromText(myThrowsCanonicalText, myMethod);
      JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(context.project());
      PsiElement ref = manager.shortenClassReferences(factory.createReferenceElementByType(type));
      return ModCommand.psiUpdate(myMethod, method -> method.getThrowsList().add(ref));
    }
  }

  public static class RemoveFirst extends MethodThrowsFix {
    public RemoveFirst(@NotNull PsiMethod method, @NotNull PsiClassType exceptionType, boolean showClassName) {
      super("fix.throws.list.remove.exception", method, exceptionType, showClassName);
    }

    @Override
    protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiMethod method) {
      PsiJavaCodeReferenceElement[] referenceElements = method.getThrowsList().getReferenceElements();
      return Arrays.stream(referenceElements)
        .filter(referenceElement -> referenceElement.getCanonicalText().equals(myThrowsCanonicalText)).findFirst()
        .map(ref -> ModCommand.psiUpdate(ref, PsiElement::delete))
        .orElse(ModCommand.nop());
    }
  }

  public static class Remove extends MethodThrowsFix {
    public Remove(@NotNull PsiMethod method, @NotNull PsiClassType exceptionType, boolean showClassName) {
      super("fix.throws.list.remove.exception", method, exceptionType, showClassName);
    }

    @Override
    protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiMethod method) {
      Project project = context.project();
      PsiClassType exception = JavaPsiFacade.getElementFactory(project).createTypeByFQClassName(myThrowsCanonicalText, method.getResolveScope());
      ModCommand action = ModCommand.psiUpdate(method, m -> processMethod(project, m));
      if (!IntentionPreviewUtils.isIntentionPreviewActive() && !ExceptionUtil.isUncheckedException(exception)) {
        JavaMethodFindUsagesOptions ops = new JavaMethodFindUsagesOptions(project);
        ops.isSearchForTextOccurrences = false;
        ops.isImplicitToString = false;
        ops.isSkipImportStatements = true;

        Map<PsiElement, ModShowConflicts.Conflict> conflicts = new HashMap<>();
        JavaFindUsagesHelper.processElementUsages(
          method, ops, usage -> {
            PsiElement element = usage.getElement();
            if (!(element instanceof PsiReferenceExpression)) return true;
            PsiElement parent = element.getParent();
            if (parent instanceof PsiCallExpression) {
              ExceptionUtil.HandlePlace place = ExceptionUtil.getHandlePlace(parent, exception, null);
              if (place instanceof ExceptionUtil.HandlePlace.TryCatch tryCatch) {
                PsiParameter parameter = tryCatch.getParameter();
                PsiCodeBlock block = tryCatch.getTryStatement().getTryBlock();
                if (block != null) {
                  PsiCallExpression call = (PsiCallExpression)parent;
                  Collection<PsiClassType> types = ExceptionUtil.collectUnhandledExceptions(block, null, call);
                  if (types.contains(exception)) {
                    return true;
                  }
                  List<PsiClassType> thrownCheckedExceptions = new ArrayList<>(ExceptionUtil.getThrownCheckedExceptions(call));
                  thrownCheckedExceptions.remove(exception);

                  PsiType caughtExceptionType = parameter.getType();
                  if (Stream.concat(types.stream(), thrownCheckedExceptions.stream())
                    .noneMatch(ex -> caughtExceptionType.isAssignableFrom(ex))) {
                    conflicts.put(parameter, new ModShowConflicts.Conflict(List.of(
                      JavaAnalysisBundle.message("exception.handler.will.become.unreachable"))));
                  }
                }
              }
            }
            return true;
          });

        return ModCommand.showConflicts(conflicts).andThen(action);
      }
      return action;
    }

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

  @Override
  public final @NotNull String getFamilyName() {
    return QuickFixBundle.message("fix.throws.list.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethod element) {
    if (element.getThrowsList() instanceof PsiCompiledElement) return null; // can happen in Kotlin
    return Presentation.of(QuickFixBundle.message(myMessageKey, StringUtil.getShortName(myThrowsCanonicalText), myMethodName));
  }
}
