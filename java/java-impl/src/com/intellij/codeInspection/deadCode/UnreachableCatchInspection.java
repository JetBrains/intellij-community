// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.UnhandledExceptions;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteCatchFix;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteMultiCatchFix;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.codeserver.highlighting.JavaErrorCollector;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public final class UnreachableCatchInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitTryStatement(@NotNull PsiTryStatement statement) {
        UnhandledExceptions thrownTypes = UnhandledExceptions.fromTryStatement(statement);
        if (thrownTypes.hasUnresolvedCalls()) return;
        for (PsiParameter parameter : statement.getCatchBlockParameters()) {
          checkWithImprovedCatchAnalysis(statement, parameter, thrownTypes.exceptions());
        }
      }

      void checkWithImprovedCatchAnalysis(@NotNull PsiTryStatement statement, @NotNull PsiParameter parameter,
                                          @NotNull Collection<? extends PsiClassType> thrownInTryStatement) {
        PsiElement scope = parameter.getDeclarationScope();
        if (!(scope instanceof PsiCatchSection catchSection)) return;

        PsiCatchSection[] allCatchSections = catchSection.getTryStatement().getCatchSections();
        int idx = ArrayUtilRt.find(allCatchSections, catchSection);
        if (idx <= 0) return;

        Collection<PsiClassType> thrownTypes = new HashSet<>(thrownInTryStatement);
        PsiManager manager = holder.getFile().getManager();
        GlobalSearchScope parameterResolveScope = parameter.getResolveScope();
        thrownTypes.add(PsiType.getJavaLangError(manager, parameterResolveScope));
        thrownTypes.add(PsiType.getJavaLangRuntimeException(manager, parameterResolveScope));

        List<PsiTypeElement> parameterTypeElements = PsiUtil.getParameterTypeElements(parameter);
        boolean isMultiCatch = parameterTypeElements.size() > 1;
        for (PsiTypeElement catchTypeElement : parameterTypeElements) {
          PsiType catchType = catchTypeElement.getType();
          if (ExceptionUtil.isGeneralExceptionType(catchType)) continue;

          // collect exceptions caught by this type
          List<PsiClassType> caught = new ArrayList<>();
          for (PsiClassType t : thrownTypes) {
            if (catchType.isAssignableFrom(t) || t.isAssignableFrom(catchType)) {
              caught.add(t);
            }
          }
          if (caught.isEmpty()) continue;
          Collection<PsiClassType> caughtCopy = new HashSet<>(caught);

          // exclude all caught by previous catch sections
          for (int i = 0; i < idx; i++) {
            PsiParameter prevCatchParameter = allCatchSections[i].getParameter();
            if (prevCatchParameter == null) continue;
            for (PsiTypeElement prevCatchTypeElement : PsiUtil.getParameterTypeElements(prevCatchParameter)) {
              PsiType prevCatchType = prevCatchTypeElement.getType();
              caught.removeIf(prevCatchType::isAssignableFrom);
              if (caught.isEmpty()) break;
            }
          }

          // check & warn
          if (caught.isEmpty()) {
            if (JavaErrorCollector.findSingleError(statement) != null) return;
            String types = caughtCopy.stream().map(JavaHighlightUtil::formatType).collect(NlsMessages.joiningAnd());
            String message = JavaAnalysisBundle.message("inspection.unreachable.catch.message", types, caughtCopy.size());
            ModCommandAction action = isMultiCatch ?
                                      new DeleteMultiCatchFix(catchTypeElement) :
                                      new DeleteCatchFix(parameter);
            holder.problem(catchSection.getFirstChild(), message).fix(action).register();
          }
        }
      }
    };
  }
}
