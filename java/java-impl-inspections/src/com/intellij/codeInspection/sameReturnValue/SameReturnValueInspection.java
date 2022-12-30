// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sameReturnValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;

public class SameReturnValueInspection extends GlobalJavaBatchInspectionTool {
  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity,
                                                           @NotNull AnalysisScope scope,
                                                           @NotNull InspectionManager manager,
                                                           @NotNull GlobalInspectionContext globalContext,
                                                           @NotNull ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refEntity;

      if (refMethod.isConstructor()) return null;
      if (refMethod.hasSuperMethods()) return null;

      String returnValue = refMethod.getReturnValueIfSame();
      if (returnValue != null) {
        final UMethod method = (UMethod)refMethod.getUastElement();
        final PsiType returnType = method.getReturnType();
        if (returnType == null || returnType.equalsToText(CommonClassNames.JAVA_LANG_VOID)) {
          return null;
        }

        final String message;
        if (refMethod.getDerivedReferences().isEmpty()) {
          message = JavaAnalysisBundle.message("inspection.same.return.value.problem.descriptor", returnValue);
        } else if (refMethod.hasBody()) {
          message = JavaAnalysisBundle.message("inspection.same.return.value.problem.descriptor1", returnValue);
        } else {
          message = JavaAnalysisBundle.message("inspection.same.return.value.problem.descriptor2", returnValue);
        }

        UElement anchor = method.getUastAnchor();
        if (anchor != null) {
          PsiElement psiAnchor = anchor.getSourcePsi();
          if (psiAnchor != null) {
            return new ProblemDescriptor[] {
              manager.createProblemDescriptor(psiAnchor, message, false, null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            };
          }
        }
      }
    }

    return null;
  }


  @Override
  protected boolean queryExternalUsagesRequests(@NotNull RefManager manager,
                                                @NotNull GlobalJavaInspectionContext globalContext,
                                                @NotNull ProblemDescriptionsProcessor processor) {
    manager.iterate(new RefJavaVisitor() {
      @Override public void visitElement(@NotNull RefEntity refEntity) {
        if (refEntity instanceof RefElement && processor.getDescriptions(refEntity) != null) {
          refEntity.accept(new RefJavaVisitor() {
            @Override public void visitMethod(@NotNull final RefMethod refMethod) {
              if (PsiModifier.PRIVATE.equals(refMethod.getAccessModifier())) return;
              globalContext.enqueueDerivedMethodsProcessor(refMethod, derivedMethod -> {
                processor.ignoreElement(refMethod);
                return false;
              });
            }
          });
        }
      }
    });

    return false;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.declaration.redundancy");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "SameReturnValue";
  }
}
