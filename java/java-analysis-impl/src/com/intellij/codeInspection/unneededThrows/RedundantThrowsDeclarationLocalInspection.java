/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.unneededThrows;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.MethodThrowsFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.JavaOverridingMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author anna
 * @since 15-Nov-2005
 */
public class RedundantThrowsDeclarationLocalInspection extends BaseJavaBatchLocalInspectionTool implements CleanupLocalInspectionTool {
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
  public String getDisplayName() {
    return myGlobalTool.getDisplayName();
  }

  @Override
  @NotNull
  public String getShortName() {
    return myGlobalTool.getShortName();
  }

  @Nullable
  @Override
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return checkExceptionsNeverThrown(method, manager);
  }

  @Nullable
  private ProblemDescriptor[] checkExceptionsNeverThrown(PsiMethod method,
                                                         InspectionManager inspectionManager) {
    if (method instanceof SyntheticElement) return null;
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || JavaHighlightUtil.isSerializationRelatedMethod(method, containingClass)) return null;

    PsiCodeBlock body = method.getBody();
    if (body == null) return null;

    if (myGlobalTool.IGNORE_ENTRY_POINTS && UnusedDeclarationInspectionBase.findUnusedDeclarationInspection(method).isEntryPoint(method)) {
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

      Set<String> thrownExceptionShortNames = candidates.stream().map(refAndType -> refAndType.type.getClassName()).collect(Collectors.toSet());
      Predicate<PsiMethod> methodContainsThrownExceptions = m -> Arrays.stream(m.getThrowsList().getReferencedTypes())
        .map(PsiClassType::getClassName)
        .anyMatch(thrownExceptionShortNames::contains);
      Stream<PsiMethod> overridingMethods = JavaOverridingMethodUtil.getOverridingMethodsIfCheapEnough(method, null, methodContainsThrownExceptions);
      if (overridingMethods == null) return null;

      Iterator<PsiMethod> overridingMethodIt = overridingMethods.iterator();
      while (overridingMethodIt.hasNext()) {
        PsiMethod m = overridingMethodIt.next();
        PsiClassType[] overridingMethodThrownException = m.getThrowsList().getReferencedTypes();

        candidates.removeIf(refAndType -> {
          PsiClassType type = refAndType.type;
          return ArrayUtil.contains(type, overridingMethodThrownException);
        });

        if (candidates.isEmpty()) return null;
      }
    }

    return candidates.stream().map(exceptionType -> {
      PsiJavaCodeReferenceElement reference = exceptionType.ref;
      String description = JavaErrorMessages.message("exception.is.never.thrown", JavaHighlightUtil.formatType(exceptionType.type));
      LocalQuickFix quickFix = new MethodThrowsFix(method, exceptionType.type, false, false);
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

  private static class ReferenceAndType {
    private final PsiJavaCodeReferenceElement ref;
    private final PsiClassType type;

    private ReferenceAndType(@NotNull PsiJavaCodeReferenceElement ref) {
      this.ref = ref;
      type = JavaPsiFacade.getElementFactory(ref.getProject()).createType(ref);
    }
  }
}
