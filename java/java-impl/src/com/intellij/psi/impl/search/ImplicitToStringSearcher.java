// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.compiler.CompilerReferenceService;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.JavaBinaryPlusExpressionIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ImplicitToStringSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {
  private static final Logger LOG = Logger.getInstance(ImplicitToStringSearcher.class);

  @Override
  public void processQuery(@NotNull MethodReferencesSearch.SearchParameters parameters, @NotNull Processor<PsiReference> consumer) {
    PsiMethod targetMethod = parameters.getMethod();
    if (!"toString".equals(targetMethod.getName()) || targetMethod.getParameters().length != 0) {
      return;
    }
    PsiClass targetClass = ReadAction.compute(() -> targetMethod.getContainingClass());
    if (targetClass == null) {
      return;
    }
    Project project = parameters.getProject();
    DumbService dumbService = DumbService.getInstance(project);
    Map<VirtualFile, int[]> fileOffsets = new THashMap<>();
    dumbService.runReadActionInSmartMode(() -> {
      CompilerReferenceService compilerReferenceService = CompilerReferenceService.getInstance(project);
      GlobalSearchScope scopeWithoutToString = compilerReferenceService == null ? null : compilerReferenceService.getScopeWithoutImplicitToStringCodeReferences(targetClass);
      GlobalSearchScope filter = GlobalSearchScopeUtil.toGlobalSearchScope(scopeWithoutToString == null
                                                                           ? parameters.getEffectiveSearchScope()
                                                                           : GlobalSearchScope.notScope(scopeWithoutToString).intersectWith(parameters.getEffectiveSearchScope()), project);
      FileBasedIndex.getInstance().processValues(JavaBinaryPlusExpressionIndex.INDEX_ID, Boolean.TRUE, null,
                                                 (file, value) -> {
                                                   ProgressManager.checkCanceled();
                                                   fileOffsets.put(file, value.getOffsets());
                                                   return true;
                                                 }, filter);

    });

    PsiManager psiManager = PsiManager.getInstance(project);
    for (Map.Entry<VirtualFile, int[]> entry : fileOffsets.entrySet()) {
      VirtualFile file = entry.getKey();
      int[] offsets = entry.getValue();
      ProgressManager.checkCanceled();
      if (!processFile(file, offsets, psiManager, targetMethod, consumer)) {
        return;
      }
    }
  }

  private static boolean processFile(VirtualFile file,
                                     int[] offsets,
                                     PsiManager manager,
                                     PsiMethod targetMethod,
                                     Processor<PsiReference> consumer) {
    return ReadAction.compute(() -> {
      PsiFile psiFile = ObjectUtils.notNull(manager.findFile(file));
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
        PsiBinaryExpression binaryExpr = ObjectUtils.tryCast(plusToken.getParent(), PsiBinaryExpression.class);
        if (binaryExpr == null) {
          LOG.error("binaryExpr shouldn't be null for binary op  in " + psiFile + " at " + offset);
          continue;
        }

        PsiExpression lOperand = binaryExpr.getLOperand();
        PsiExpression rOperand = binaryExpr.getROperand();
        if (rOperand == null) {
          LOG.error("rOperand shouldn't be null for binary op in " + psiFile + " at " + offset);
          continue;
        }

        if (!processBinaryExpression(lOperand, rOperand, consumer, targetMethod)) {
          return false;
        }
      }
      return true;
    });
  }

  private static boolean processBinaryExpression(@NotNull PsiExpression lhs,
                                                 @NotNull PsiExpression rhs,
                                                 @NotNull Processor<PsiReference> consumer,
                                                 @NotNull PsiMethod targetMethod) {
    if (!processBinaryExpressionInOneDirection(lhs, rhs, consumer, targetMethod)) {
      return false;
    }
    return processBinaryExpressionInOneDirection(rhs, lhs, consumer, targetMethod);
  }

  private static boolean processBinaryExpressionInOneDirection(@NotNull PsiExpression stringExpr,
                                                               @NotNull PsiExpression expr,
                                                               @NotNull Processor<PsiReference> consumer,
                                                               @NotNull PsiMethod targetMethod) {
    PsiType strType = stringExpr.getType();
    if (strType == null || !strType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) return true;

    PsiType type = expr.getType();
    if (type instanceof PsiPrimitiveType) {
      type = ((PsiPrimitiveType)type).getBoxedType(expr);
    }
    PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (aClass != null && !CommonClassNames.JAVA_LANG_STRING.equals(aClass.getQualifiedName())) {
      PsiMethod implicitlyUsedMethod = aClass.findMethodBySignature(targetMethod, true);
      if (implicitlyUsedMethod != null && (targetMethod == implicitlyUsedMethod || MethodSignatureUtil.isSuperMethod(targetMethod, implicitlyUsedMethod))) {
        return consumer.process(new MyImplicitToStringReference(expr, targetMethod));
      }
    }
    return true;
  }

  private static class MyImplicitToStringReference implements PsiReference {
    private final PsiElement myPlace;
    private final PsiMethod myTargetMethod;

    private MyImplicitToStringReference(PsiElement place, PsiMethod method) {
      myPlace = place;
      myTargetMethod = method;
    }

    @Override
    public PsiElement getElement() {
      return myPlace;
    }

    @Override
    public TextRange getRangeInElement() {
      return new TextRange(0, myPlace.getTextLength());
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return myTargetMethod;
    }

    @NotNull
    @Override
    public String getCanonicalText() {
      return myPlace.getText();
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return null;
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      return null;
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
      return element == myTargetMethod;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public boolean isSoft() {
      return false;
    }
  }
}
