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

package com.intellij.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;

import java.util.List;

/**
 * @author yole
 * @author Konstantin Bulenkov
 */
public class LanguageFolding extends LanguageExtension<FoldingBuilder> {
  public static final LanguageFolding INSTANCE = new LanguageFolding();

  private LanguageFolding() {
    super("com.intellij.lang.foldingBuilder");
  }

  @Override
  public FoldingBuilder forLanguage(Language l) {
    FoldingBuilder cached = l.getUserData(getLanguageCache());
    if (cached != null) return cached;

    List<FoldingBuilder> extensions = forKey(l);
    FoldingBuilder result;
    if (extensions.isEmpty()) {

      Language base = l.getBaseLanguage();
      if (base != null) {
        result = forLanguage(base);
      }
      else {
        result = getDefaultImplementation();
      }
    }
    else {
      return (extensions.size() == 1) ? extensions.get(0) : new CompositeFoldingBuilder(extensions);
    }

    l.putUserData(getLanguageCache(), result);
    return result;
  }

  public static FoldingDescriptor[] buildFoldingDescriptors(FoldingBuilder builder, PsiElement root, Document document, boolean quick) {
    if (!DumbService.isDumbAware(builder) && DumbService.getInstance(root.getProject()).isDumb()) {
      return FoldingDescriptor.EMPTY;
    }

    if (builder instanceof FoldingBuilderEx) {
      return ((FoldingBuilderEx)builder).buildFoldRegions(root, document, quick);
    }
    final ASTNode astNode = root.getNode();
    if (astNode == null || builder == null) {
      return FoldingDescriptor.EMPTY;
    }

    return builder.buildFoldRegions(astNode, document);
  }
}