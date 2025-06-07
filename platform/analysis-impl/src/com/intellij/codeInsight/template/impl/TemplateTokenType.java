// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.impl;

import com.intellij.lang.Language;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface TemplateTokenType {
  IElementType TEXT = new IElementType("TEXT", Language.ANY);
  IElementType VARIABLE = new IElementType("VARIABLE", Language.ANY);
  IElementType ESCAPE_DOLLAR = new IElementType("ESCAPE_DOLLAR", Language.ANY);
}