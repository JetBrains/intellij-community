// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;
import org.jetbrains.annotations.Contract;

public interface MergeableElement {
  @Contract("_ -> this")
  MergeableElement mergeWith(MergeableElement other);

  boolean shouldBeMergedIntoAnother();
}
