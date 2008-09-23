package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMethodUtil;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.util.TextRange;

public class DefaultQuickFixProvider implements UnresolvedReferenceQuickFixProvider{
  public void registerFixes(PsiJavaCodeReferenceElement ref, QuickFixActionRegistrar registrar) {
    registrar.register(new ImportClassFix(ref));
    registrar.register(SetupJDKFix.getInstnace());
    OrderEntryFix.registerFixes(registrar, ref);
    MoveClassToModuleFix.registerFixes(registrar, ref);

    if ((ref instanceof PsiReferenceExpression)) {
      TextRange fixRange = HighlightMethodUtil.getFixRange(ref);
      PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;

      registrar.register(fixRange, new CreateEnumConstantFromUsageFix(refExpr), null);
      registrar.register(fixRange, new CreateConstantFieldFromUsageFix(refExpr), null);
      registrar.register(fixRange, new CreateFieldFromUsageFix(refExpr), null);
      registrar.register(new RenameWrongRefFix(refExpr));

      if (!ref.isQualified()) {
        registrar.register(fixRange, new BringVariableIntoScopeFix(refExpr), null);
        registrar.register(fixRange, new CreateLocalFromUsageFix(refExpr), null);
        registrar.register(fixRange, new CreateParameterFromUsageFix(refExpr), null);
      }
    }

    registrar.register(new CreateClassFromUsageFix(ref, CreateClassKind.INTERFACE));
    registrar.register(new CreateClassFromUsageFix(ref, CreateClassKind.ENUM));
    PsiElement parent = PsiTreeUtil.getParentOfType(ref, PsiNewExpression.class, PsiMethod.class);
    if (parent instanceof PsiNewExpression && !(ref.getParent() instanceof PsiTypeElement)) {
      registrar.register(new CreateClassFromNewFix((PsiNewExpression)parent));
      registrar.register(new CreateInnerClassFromNewFix((PsiNewExpression)parent));
    }
    else {
      registrar.register(new CreateClassFromUsageFix(ref, CreateClassKind.CLASS));
      registrar.register(new CreateInnerClassFromUsageFix(ref, CreateClassKind.CLASS));
    }
  }
}