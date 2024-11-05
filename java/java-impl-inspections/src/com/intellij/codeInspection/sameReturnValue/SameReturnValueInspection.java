// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sameReturnValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.uast.UastHintedVisitorAdapter;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.List;

public final class SameReturnValueInspection extends GlobalJavaBatchInspectionTool {
  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity,
                                                           @NotNull AnalysisScope scope,
                                                           @NotNull InspectionManager manager,
                                                           @NotNull GlobalInspectionContext globalContext,
                                                           @NotNull ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof RefMethod refMethod) {

      if (refMethod.isConstructor()) return null;
      if (refMethod.hasSuperMethods()) return null;

      String returnValue = refMethod.getReturnValueIfSame();
      if (returnValue != null) {
        final UMethod method = refMethod.getUastElement();
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
      @Override
      public void visitMethod(@NotNull final RefMethod refMethod) {
        if (processor.getDescriptions(refMethod) == null) return;
        if (PsiModifier.PRIVATE.equals(refMethod.getAccessModifier())) return;
        globalContext.enqueueDerivedMethodsProcessor(refMethod, derivedMethod -> {
          processor.ignoreElement(refMethod);
          return false;
        });
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

  @Nullable
  @Override
  public LocalInspectionTool getSharedLocalInspectionTool() {
    return new LocalSameReturnValueInspection(this);
  }

  @SuppressWarnings("InspectionDescriptionNotFoundInspection") // TODO IJPL-166089
  private static final class LocalSameReturnValueInspection extends AbstractBaseUastLocalInspectionTool {
    private final SameReturnValueInspection myGlobal;

    private LocalSameReturnValueInspection(SameReturnValueInspection global) {
      myGlobal = global;
    }

    @Override
    public boolean runForWholeFile() {
      return true;
    }

    @Override
    @NotNull
    public String getGroupDisplayName() {
      return myGlobal.getGroupDisplayName();
    }

    @Override
    @NotNull
    public String getShortName() {
      return myGlobal.getShortName();
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
      @SuppressWarnings("unchecked") Class<? extends UMethod>[] hint = new Class[]{UMethod.class};

      return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {

        @Override
        public boolean visitMethod(@NotNull UMethod method) {
          UElement anchor = method.getUastAnchor();
          if (anchor == null) return true;
          PsiElement psiAnchor = anchor.getSourcePsi();
          if (psiAnchor == null) return true;
          PsiMethod javaMethod = method.getJavaPsi();
          final PsiType returnType = javaMethod.getReturnType();
          if (returnType == null || returnType.equalsToText(CommonClassNames.JAVA_LANG_VOID)) {
            return true;
          }
          if (javaMethod.findSuperMethods().length != 0 || PsiUtil.canBeOverridden(javaMethod)) {
            return true;
          }
          List<UExpression> returnExpressions = new SmartList<>();
          final UExpression body = method.getUastBody();
          if (body == null) return true;
          body.accept(new AbstractUastVisitor() {
            @Override
            public boolean visitReturnExpression(@NotNull UReturnExpression node) {
              if (node.getJumpTarget() == method) {
                UExpression returnExpression = node.getReturnExpression();
                if (returnExpression != null) {
                  StreamEx.of(UastUtils.nonStructuralChildren(returnExpression)).into(returnExpressions);
                }
              }
              return super.visitReturnExpression(node);
            }
          });
          if (returnExpressions.size() < 2) return true;
          String returnValue = RefMethodImpl.createReturnValueTemplate(returnExpressions.get(0));
          if (returnValue != null &&
              ContainerUtil.all(returnExpressions,
                                returnExpression -> returnValue.equals(RefMethodImpl.createReturnValueTemplate(returnExpression)))) {
            String message = JavaAnalysisBundle.message("inspection.same.return.value.problem.descriptor", returnValue);
            holder.registerProblem(psiAnchor, message);
          }
          return true;
        }
      }, hint);
    }
  }
}
