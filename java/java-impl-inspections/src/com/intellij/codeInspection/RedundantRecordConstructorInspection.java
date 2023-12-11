// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

public final class RedundantRecordConstructorInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.RECORDS.isAvailable(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        PsiClass aClass = method.getContainingClass();
        if (aClass == null || !aClass.isRecord()) return;
        if (JavaPsiRecordUtil.isCompactConstructor(method)) {
          checkCompact(method);
        }
        else {
          checkCanonical(method);
        }
      }

      private void checkCanonical(PsiMethod ctor) {
        ConstructorSimplifier simplifier = createCtorSimplifier(ctor);
        if (simplifier instanceof RemoveRedundantCtorSimplifier) {
          PsiIdentifier nameIdentifier = ctor.getNameIdentifier();
          if (nameIdentifier == null) return;
          holder.registerProblem(nameIdentifier, JavaBundle.message("inspection.redundant.record.constructor.canonical.message"),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, simplifier);
        }
        else if (simplifier instanceof MakeCtorCompactSimplifier) {
          holder.registerProblem(ctor.getParameterList(),
                                 JavaBundle.message("inspection.redundant.record.constructor.can.be.compact.message"),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, simplifier);
        }
      }

      private void checkCompact(PsiMethod ctor) {
        PsiCodeBlock body = ctor.getBody();
        if (body == null) return;
        PsiIdentifier ctorNameIdentifier = ctor.getNameIdentifier();
        if (ctorNameIdentifier == null) return;
        PsiStatement[] statements = body.getStatements();
        if (statements.length > 0) {
          PsiParameter[] parameters = ctor.getParameterList().getParameters();
          PsiRecordComponent[] components = Objects.requireNonNull(ctor.getContainingClass()).getRecordComponents();
          int count = getAssignedComponentsCount(components, parameters, statements);
          if (count < statements.length) {
            for (int i = statements.length - count; i < statements.length; i++) {
              holder.problem(statements[i], JavaBundle.message("inspection.redundant.record.constructor.statement.message"))
                .fix(new DeleteElementFix(statements[i])).register();
            }
            return;
          }
        }
        if (ctor.getModifierList().getAnnotations().length == 0 &&
            ctor.getDocComment() == null) {
          holder.registerProblem(ctorNameIdentifier,
                                 JavaBundle.message("inspection.redundant.record.constructor.compact.message"),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new RemoveRedundantCtorSimplifier());
        }
      }
    };
  }

  @Nullable
  public static ConstructorSimplifier createCtorSimplifier(@NotNull PsiMethod ctor) {
    if (!JavaPsiRecordUtil.isExplicitCanonicalConstructor(ctor)) return null;
    PsiCodeBlock body = ctor.getBody();
    if (body == null) return null;
    PsiIdentifier nameIdentifier = ctor.getNameIdentifier();
    if (nameIdentifier == null) return null;
    PsiRecordComponent[] components = Objects.requireNonNull(ctor.getContainingClass()).getRecordComponents();
    PsiParameter[] parameters = ctor.getParameterList().getParameters();
    PsiAnnotation.TargetType[] targets = {PsiAnnotation.TargetType.PARAMETER, PsiAnnotation.TargetType.TYPE_USE};
    if (!EntryStream.zip(components, parameters)
      .mapKeys(c -> ContainerUtil.filter(c.getAnnotations(), anno -> AnnotationTargetUtil.findAnnotationTarget(anno, targets) != null))
      .mapValues(p -> Arrays.asList(p.getAnnotations()))
      .allMatch((compAnnotations, paramAnnotations) ->
        compAnnotations.size() == paramAnnotations.size() &&
        EntryStream.zip(compAnnotations, paramAnnotations).allMatch(PsiEquivalenceUtil::areElementsEquivalent))) {
      return null;
    }
    PsiStatement[] statements = body.getStatements();
    int assignedCount = getAssignedComponentsCount(components, parameters, statements);
    if (statements.length == components.length && assignedCount == components.length &&
        ctor.getModifierList().getAnnotations().length == 0 && ctor.getDocComment() == null) {
      return new RemoveRedundantCtorSimplifier();
    }
    if (PsiUtil.findReturnStatements(body).length > 0) return null;
    if (assignedCount != components.length) {
      return null;
    }
    return new MakeCtorCompactSimplifier();
  }

  private static int getAssignedComponentsCount(PsiRecordComponent @NotNull [] components,
                                                PsiParameter @NotNull [] parameters,
                                                PsiStatement @NotNull [] statements) {
    assert parameters.length == components.length;
    Set<PsiRecordComponent> unprocessed = ContainerUtil.newHashSet(components);
    int i = statements.length - 1;
    while (i >= 0 && !unprocessed.isEmpty()) {
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(statements[i]);
      if (assignment == null) break;
      PsiReferenceExpression lValue = ObjectUtils.tryCast(
        PsiUtil.skipParenthesizedExprDown(assignment.getLExpression()), PsiReferenceExpression.class);
      if (lValue == null || !ExpressionUtil.isEffectivelyUnqualified(lValue)) break;
      PsiField field = ObjectUtils.tryCast(lValue.resolve(), PsiField.class);
      if (field == null) break;
      PsiRecordComponent component = JavaPsiRecordUtil.getComponentForField(field);
      if (component == null || !unprocessed.contains(component)) break;
      PsiParameter parameter = parameters[ArrayUtil.indexOf(components, component)];
      if (!parameter.getName().equals(component.getName())) break;
      PsiReferenceExpression rValue = ObjectUtils.tryCast(
        PsiUtil.skipParenthesizedExprDown(assignment.getRExpression()), PsiReferenceExpression.class);
      if (rValue == null || rValue.getQualifierExpression() != null || !parameter.getName().equals(rValue.getReferenceName())) break;
      unprocessed.remove(component);
      i--;
    }
    return components.length - unprocessed.size();
  }

  public static abstract class ConstructorSimplifier extends PsiUpdateModCommandQuickFix {
    public abstract void simplify(@NotNull PsiMethod ctor);

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethod ctor = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (ctor != null) {
        simplify(ctor);
      }
    }
  }

  private static class RemoveRedundantCtorSimplifier extends ConstructorSimplifier {
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.remove.title", JavaElementKind.CONSTRUCTOR.object());
    }

    @Override
    public void simplify(@NotNull PsiMethod ctor) {
      new CommentTracker().deleteAndRestoreComments(ctor);
    }
  }

  private static class MakeCtorCompactSimplifier extends ConstructorSimplifier {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.redundant.record.constructor.fix.family.name");
    }

    @Override
    public void simplify(@NotNull PsiMethod ctor) {
      if (!JavaPsiRecordUtil.isExplicitCanonicalConstructor(ctor)) return;
      PsiClass record = ctor.getContainingClass();
      if (record == null) return;
      PsiCodeBlock body = ctor.getBody();
      if (body == null) return;
      PsiRecordComponent[] components = record.getRecordComponents();
      PsiParameterList parameterList = ctor.getParameterList();
      PsiParameter[] parameters = parameterList.getParameters();
      PsiStatement[] statements = body.getStatements();
      int assignedComponents = getAssignedComponentsCount(components, parameters, statements);
      PsiStatement firstStatementToDelete = assignedComponents == 0 ? null : statements[statements.length - assignedComponents];
      StringBuilder resultText = new StringBuilder();
      for (PsiElement child : ctor.getChildren()) {
        if (child == parameterList) continue;
        if (child == body) break;
        resultText.append(child.getText());
      }
      boolean skipStatements = false;
      CommentTracker ct = new CommentTracker();
      for (PsiElement child : body.getChildren()) {
        if (child == firstStatementToDelete) {
          skipStatements = true;
        }
        if (skipStatements && child.getNextSibling() != null) {
          ct.grabComments(child);
          continue;
        }
        resultText.append(child.getText());
      }
      PsiMethod compactCtor = JavaPsiFacade.getElementFactory(record.getProject()).createMethodFromText(resultText.toString(), ctor);
      PsiMethod result = (PsiMethod)ctor.replace(compactCtor);
      ct.insertCommentsBefore(Objects.requireNonNull(Objects.requireNonNull(result.getBody()).getRBrace()));
    }
  }
}
