// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.patterns.ElementPattern;

public interface CompletionProcessBase extends CompletionProcess {

  void addWatchedPrefix(int startOffset, ElementPattern<String> restartCondition);

}
