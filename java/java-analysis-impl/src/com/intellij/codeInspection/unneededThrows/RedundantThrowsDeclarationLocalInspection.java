// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.unneededThrows;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.MethodThrowsFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.psi.*;
import com.siyeh.ig.JavaOverridingMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("InspectionDescriptionNotFoundInspection") // delegates
public class RedundantThrowsDeclarationLocalInspection extends AbstractBaseJavaLocalInspectionTool {
  private final RedundantThrowsDeclarationInspection myGlobalTool;

  @TestOnly
  public RedundantThrowsDeclarationLocalInspection() {this(new RedundantThrowsDeclarationInspection());}

  public RedundantThrowsDeclarationLocalInspection(@NotNull RedundantThrowsDeclarationInspection tool) {myGlobalTool = tool;}

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return myGlobalTool.getGroupDisplayName();
  }

  @Override
  @NotNull
  public String getShortName() {
    return myGlobalTool.getShortName();
  }

  @Override
  public ProblemDescriptor @Nullable [] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return checkExceptionsNeverThrown(method, manager);
  }

  private ProblemDescriptor @Nullable [] checkExceptionsNeverThrown(PsiMethod method,
                                                                    InspectionManager inspectionManager) {
    if (method instanceof SyntheticElement) return null;
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || JavaHighlightUtil.isSerializationRelatedMethod(method, containingClass)) return null;

    PsiCodeBlock body = method.getBody();
    if (body == null) return null;

    if (myGlobalTool.IGNORE_ENTRY_POINTS && UnusedDeclarationInspectionBase.isDeclaredAsEntryPoint(method)) {
      return null;
    }

    ReferenceAndType[] thrownExceptions = getThrownCheckedExceptions(method);
    if (thrownExceptions.length == 0) return null;

    PsiModifierList modifierList = method.getModifierList();
    boolean needCheckOverridingMethods = !(modifierList.hasModifierProperty(PsiModifier.PRIVATE) ||
                                            modifierList.hasModifierProperty(PsiModifier.STATIC) ||
                                            modifierList.hasModifierProperty(PsiModifier.FINAL) ||
                                            method.isConstructor() ||
                                            containingClass instanceof PsiAnonymousClass ||
                                            containingClass.hasModifierProperty(PsiModifier.FINAL));
    Collection<PsiClassType> unhandled = RedundantThrowsGraphAnnotator.getUnhandledExceptions(body, method, containingClass);
    List<ReferenceAndType> candidates = Arrays.stream(thrownExceptions)
      .filter(refAndType -> unhandled.stream().noneMatch(unhandledException -> unhandledException.isAssignableFrom(refAndType.type) || refAndType.type.isAssignableFrom(unhandledException)))
      .collect(Collectors.toList());

    if (candidates.isEmpty()) return null;
    if (needCheckOverridingMethods) {
      Predicate<PsiMethod> methodContainsThrownExceptions = m -> m.getThrowsList().getReferencedTypes().length != 0;
      Stream<PsiMethod> overridingMethods = JavaOverridingMethodUtil.getOverridingMethodsIfCheapEnough(method, null, methodContainsThrownExceptions);
      if (overridingMethods == null) return null;

      Iterator<PsiMethod> overridingMethodIt = overridingMethods.iterator();
      while (overridingMethodIt.hasNext()) {
        PsiMethod m = overridingMethodIt.next();
        PsiClassType[] overridingMethodThrownExceptions = m.getThrowsList().getReferencedTypes();

        candidates.removeIf(refAndType -> {
          PsiClassType type = refAndType.type;
          return Arrays.stream(overridingMethodThrownExceptions).anyMatch(type::isAssignableFrom);
        });

        if (candidates.isEmpty()) return null;
      }
    }

    return candidates.stream().map(exceptionType -> {
      PsiJavaCodeReferenceElement reference = exceptionType.ref;
      String description = JavaErrorBundle.message("exception.is.never.thrown", JavaHighlightUtil.formatType(exceptionType.type));
      LocalQuickFix quickFix = new MethodThrowsFix.Remove(method, exceptionType.type, false);
      return inspectionManager.createProblemDescriptor(reference, description, quickFix, ProblemHighlightType.LIKE_UNUSED_SYMBOL, true);
    }).toArray(ProblemDescriptor[]::new);
  }

  private static ReferenceAndType[] getThrownCheckedExceptions(PsiMethod method) {
    return Stream
      .of(method.getThrowsList().getReferenceElements())
      .map(ref -> {
        PsiElement resolved = ref.resolve();
        return resolved instanceof PsiClass && !ExceptionUtil.isUncheckedException((PsiClass)resolved) ? new ReferenceAndType(ref) : null;
      })
      .filter(Objects::nonNull)
      .toArray(ReferenceAndType[]::new);
  }

  private static final class ReferenceAndType {
    private final PsiJavaCodeReferenceElement ref;
    private final PsiClassType type;

    private ReferenceAndType(@NotNull PsiJavaCodeReferenceElement ref) {
      this.ref = ref;
      type = JavaPsiFacade.getElementFactory(ref.getProject()).createType(ref);
    }
  }
}
