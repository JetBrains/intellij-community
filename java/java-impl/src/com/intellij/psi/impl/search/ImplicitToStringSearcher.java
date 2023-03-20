// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.compiler.CompilerReferenceService;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.JavaBinaryPlusExpressionIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import com.intellij.psi.search.searches.ImplicitToStringSearch;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ImplicitToStringSearcher extends QueryExecutorBase<PsiExpression, ImplicitToStringSearch.SearchParameters> {
  private static final Logger LOG = Logger.getInstance(ImplicitToStringSearcher.class);

  @Override
  public void processQuery(@NotNull ImplicitToStringSearch.SearchParameters parameters, @NotNull Processor<? super PsiExpression> consumer) {
    PsiMethod targetMethod = parameters.getTargetMethod();
    Project project = PsiUtilCore.getProjectInReadAction(targetMethod);
    PsiClass aClass = ReadAction.compute(() -> targetMethod.getContainingClass());
    if (aClass == null) return;
    DumbService dumbService = DumbService.getInstance(project);
    Map<VirtualFile, int[]> fileOffsets = new HashMap<>();
    dumbService.runReadActionInSmartMode(() -> {
      CompilerReferenceService compilerReferenceService = CompilerReferenceService.getInstanceIfEnabled(project);
      GlobalSearchScope scopeWithToString = compilerReferenceService == null ? null : compilerReferenceService.getScopeWithImplicitToStringCodeReferences(aClass);
      GlobalSearchScope filter = GlobalSearchScopeUtil.toGlobalSearchScope(scopeWithToString == null
                                                                           ? parameters.getSearchScope()
                                                                           : scopeWithToString.intersectWith(parameters.getSearchScope()), project);
      FileBasedIndex.getInstance().processValues(JavaBinaryPlusExpressionIndex.INDEX_ID, Boolean.TRUE, null,
                                                 (file, value) -> {
                                                   ProgressManager.checkCanceled();
                                                   fileOffsets.put(file, value.getOffsets());
                                                   return true;
                                                 }, filter);

    });

    PsiManager psiManager = PsiManager.getInstance(project);
    psiManager.runInBatchFilesMode(() -> {
      for (Map.Entry<VirtualFile, int[]> entry : fileOffsets.entrySet()) {
        VirtualFile file = entry.getKey();
        int[] offsets = entry.getValue();
        ProgressManager.checkCanceled();
        if (!processFile(file, offsets, psiManager, targetMethod, consumer, dumbService)) {
          return null;
        }
      }
      return null;
    });
  }

  private static boolean processFile(VirtualFile file,
                                     int[] offsets,
                                     PsiManager manager,
                                     PsiMethod targetMethod,
                                     Processor<? super PsiExpression> consumer,
                                     DumbService dumbService) {
    return dumbService.runReadActionInSmartMode(() -> {
      PsiFile psiFile = Objects.requireNonNull(manager.findFile(file));
      if (!(psiFile instanceof PsiJavaFile)) {
        LOG.error("Non-java file " + psiFile + "; " + file);
        return true;
      }

      for (int offset : offsets) {
        PsiJavaToken plusToken = PsiTreeUtil.findElementOfClassAtOffset(psiFile, offset, PsiJavaToken.class, false);
        if (plusToken == null) {
          LOG.error("plusToken shouldn't be null in " + psiFile + " at " + offset);
          continue;
        }
        PsiElement parent = plusToken.getParent();

        if (parent instanceof PsiPolyadicExpression polyadicExpression) {
          PsiType exprType = polyadicExpression.getType();
          if (exprType == null || !exprType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            continue;
          }
          PsiExpression[] operands = polyadicExpression.getOperands();
          for (PsiExpression operand : operands) {
            if (!processPolyadicExprOperand(operand, consumer, targetMethod)) {
              return false;
            }
          }
        }
      }
      return true;
    });
  }

  private static boolean processPolyadicExprOperand(@NotNull PsiExpression expr,
                                                    @NotNull Processor<? super PsiExpression> consumer,
                                                    @NotNull PsiMethod targetMethod) {
    PsiType type = expr.getType();
    if (type instanceof PsiPrimitiveType) {
      type = ((PsiPrimitiveType)type).getBoxedType(expr);
    }
    PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (aClass != null && !CommonClassNames.JAVA_LANG_STRING.equals(aClass.getQualifiedName())) {
      PsiMethod implicitlyUsedMethod = aClass.findMethodBySignature(targetMethod, true);
      if (implicitlyUsedMethod != null && (targetMethod == implicitlyUsedMethod || MethodSignatureUtil.isSuperMethod(targetMethod, implicitlyUsedMethod))) {
        return consumer.process(expr);
      }
    }
    return true;
  }
}
