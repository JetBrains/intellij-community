// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;

/**
 * @deprecated Default refactoring icon was removed because of IDEA-293415.
 * So just use {@link PsiElementBaseIntentionAction} and {@link HighPriorityAction} (if needed)
 */
@Deprecated
public abstract class BaseRefactoringIntentionAction extends PsiElementBaseIntentionAction implements HighPriorityAction {
}
