/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

/**
 * @author max
 */
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

  private static Language findLanguage(Language baseLanguage, FileViewProvider viewProvider) {
    final Set<Language> languages = viewProvider.getLanguages();
    for (final Language actualLanguage : languages) {
      if (actualLanguage.isKindOf(baseLanguage)) {
        return actualLanguage;
      }
    }
    throw new AssertionError(
        "Language " + baseLanguage + " doesn't participate in view provider " + viewProvider + ": " + new ArrayList<Language>(languages));
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
