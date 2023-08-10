// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.patterns.compiler.PatternCompilerFactory;
import com.intellij.psi.PsiElement;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Text;
import org.jetbrains.annotations.Nullable;

@Tag("pattern")
public class ElementPatternBean {

  @Attribute("type")
  public String type;

  @Text
  public String text;

  public @Nullable ElementPattern<PsiElement> compilePattern() {
    return PatternCompilerFactory.getFactory().<PsiElement>getPatternCompiler(type).compileElementPattern(text);
  }
}
