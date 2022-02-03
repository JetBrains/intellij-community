// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sameReturnValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UDeclaration;
import org.jetbrains.uast.UElement;

public class SameReturnValueInspection extends GlobalJavaBatchInspectionTool {
  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity, @NotNull AnalysisScope scope, @NotNull InspectionManager manager, @NotNull GlobalInspectionContext globalContext,
                                                           @NotNull ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refEntity;

      if (refMethod.isConstructor()) return null;
      if (refMethod.hasSuperMethods()) return null;

      String returnValue = refMethod.getReturnValueIfSame();
      if (returnValue != null) {
        final String message;
        if (refMethod.getDerivedReferences().isEmpty()) {
          message = JavaAnalysisBundle.message("inspection.same.return.value.problem.descriptor", "<code>" + returnValue + "</code>");
        } else if (refMethod.hasBody()) {
          message = JavaAnalysisBundle.message("inspection.same.return.value.problem.descriptor1", "<code>" + returnValue + "</code>");
        } else {
          message = JavaAnalysisBundle.message("inspection.same.return.value.problem.descriptor2", "<code>" + returnValue + "</code>");
        }

        final UDeclaration decl = refMethod.getUastElement();
        if (decl != null) {
          UElement anchor = decl.getUastAnchor();
          if (anchor != null) {
            PsiElement psiAnchor = anchor.getSourcePsi();
            if (psiAnchor != null) {
              return new ProblemDescriptor[] {manager.createProblemDescriptor(psiAnchor, message, false, null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)};
            }
          }
        }
      }
    }

    return null;
  }


  @Override
  protected boolean queryExternalUsagesRequests(@NotNull final RefManager manager, @NotNull final GlobalJavaInspectionContext globalContext,
                                                @NotNull final ProblemDescriptionsProcessor processor) {
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
