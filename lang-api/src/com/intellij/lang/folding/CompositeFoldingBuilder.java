/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Used by LanguageFolding class if more than one FoldingBuilder were specified
 * for a particular language.
 *
 * @author Konstantin Bulenkov
 * @see com.intellij.lang.folding.LanguageFolding
 * @since 9.0
 */
class CompositeFoldingBuilder implements FoldingBuilder {
  private final List<FoldingBuilder> myBuilders;
  private final Map<Document, Map<ASTNode, FoldingBuilder>> foldings = new HashMap<Document, Map<ASTNode, FoldingBuilder>>();
  //private final DocumentListener updater = new Updater();
  //TODO: think about old links

  CompositeFoldingBuilder(List<FoldingBuilder> builders) {
    myBuilders = builders;
  }

  public FoldingDescriptor[] buildFoldRegions(ASTNode node, Document document) {
    //document.addDocumentListener(updater);
    List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
    Map<ASTNode, FoldingBuilder> builders = foldings.get(document);
    if (builders == null) {
      builders = new HashMap<ASTNode, FoldingBuilder>();
      foldings.put(document, builders);
    }

    for (FoldingBuilder builder : myBuilders) {
      final FoldingDescriptor[] foldingDescriptors = builder.buildFoldRegions(node, document);
      if (foldingDescriptors.length > 0) {
        descriptors.addAll(Arrays.asList(foldingDescriptors));
        for (FoldingDescriptor descriptor : foldingDescriptors) {
          builders.put(descriptor.getElement(), builder);
        }
      }
    }
    return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
  }

  public String getPlaceholderText(ASTNode node) {
    final FoldingBuilder builder = findBuilderByASTNode(node);
    return builder == null ? node.getText() : builder.getPlaceholderText(node);
  }

  @Nullable
  private FoldingBuilder findBuilderByASTNode(ASTNode node) {
    final PsiElement psi = node.getPsi();
    if (psi == null) return null;

    final PsiFile file = psi.getContainingFile();
    final Document document = file == null ? null : PsiDocumentManager.getInstance(psi.getProject()).getDocument(file);
    final Map<ASTNode, FoldingBuilder> builders;

    if (document == null || (builders = foldings.get(document)) == null) return null;

    return builders.get(node);
  }

  public boolean isCollapsedByDefault(ASTNode node) {
    final FoldingBuilder builder = findBuilderByASTNode(node);
    return builder == null ? false : builder.isCollapsedByDefault(node);
  }

  //class Updater implements DocumentListener {
  //  public void beforeDocumentChange(DocumentEvent event) {
  //    foldings.remove(event.getDocument());
  //  }
  //
  //  public void documentChanged(DocumentEvent event) {
  //  }
  //}
}
