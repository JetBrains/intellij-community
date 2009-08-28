/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;

/**
 * @author peter
 */
public interface CompletionProcess {

  boolean willAutoInsert(AutoCompletionPolicy policy, final PrefixMatcher matcher);

}
