// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.shared;

import org.jetbrains.annotations.Nls;

@FunctionalInterface
public interface TextValidationFunction {
  @Nls(capitalization = Nls.Capitalization.Sentence)
  String checkText(String fieldText);
}