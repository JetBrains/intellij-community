package com.intellij.refactoring.introduceParameter;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.Map;

/**
 * @author Maxim.Medvedev
 *         Date: Apr 17, 2009 5:16:10 PM
 */
public interface IntroduceParameterMethodUsagesProcessor {
  ExtensionPointName<IntroduceParameterMethodUsagesProcessor> EP_NAME =
    new ExtensionPointName<IntroduceParameterMethodUsagesProcessor>("com.intellij.refactoring.introduceParameterMethodUsagesProcessor");

  boolean isMethodUsage(UsageInfo usage);

  Map<PsiElement, String> findConflicts(IntroduceParameterData data, UsageInfo[] usages);

  boolean processChangeMethodUsage(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages) throws IncorrectOperationException;

  boolean processChangeMethodSignature(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages) throws IncorrectOperationException;

  boolean processAddDefaultConstructor(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages);

  boolean processAddSuperCall(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages) throws IncorrectOperationException;
}
