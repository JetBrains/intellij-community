// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.extapi.psi;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Set;

public abstract class PsiFileBase extends PsiFileImpl {
  @NotNull private final Language myLanguage;
  @NotNull private final ParserDefinition myParserDefinition;

  protected PsiFileBase(@NotNull FileViewProvider viewProvider, @NotNull Language language) {
    super(viewProvider);
    myLanguage = findLanguage(language, viewProvider);
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(myLanguage);
    if (parserDefinition == null) {
      throw new RuntimeException("PsiFileBase: language.getParserDefinition() returned null for: "+myLanguage);
    }
    myParserDefinition = parserDefinition;
    final IFileElementType nodeType = parserDefinition.getFileNodeType();
    assert nodeType.getLanguage() == myLanguage: nodeType.getLanguage() + " != " + myLanguage;
    init(nodeType, nodeType);
  }

  private static Language findLanguage(@NotNull Language baseLanguage, @NotNull FileViewProvider viewProvider) {
    final Set<Language> languages = viewProvider.getLanguages();
    Language candidate = null;
    for (final Language actualLanguage : languages) {
      if (actualLanguage.equals(baseLanguage)) {
        return baseLanguage;
      }
      if (candidate == null && actualLanguage.isKindOf(baseLanguage)) {
        candidate = actualLanguage;
      }
    }
    if (candidate != null) {
      return candidate;
    }
    throw new AssertionError(
        "Language " + baseLanguage + " doesn't participate in view provider " + viewProvider + ": " + new ArrayList<>(languages));
  }

  @Override
  @NotNull
  public final Language getLanguage() {
    return myLanguage;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitFile(this);
  }

  @NotNull
  public ParserDefinition getParserDefinition() {
    return myParserDefinition;
  }
}
