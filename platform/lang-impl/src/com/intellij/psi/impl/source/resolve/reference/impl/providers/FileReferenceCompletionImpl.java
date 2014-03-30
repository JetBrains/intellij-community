/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.FilteringProcessor;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class FileReferenceCompletionImpl extends FileReferenceCompletion {
  private static final TObjectHashingStrategy<PsiElement> VARIANTS_HASHING_STRATEGY = new TObjectHashingStrategy<PsiElement>() {
    @Override

    public int computeHashCode(final PsiElement object) {
      if (object instanceof PsiNamedElement) {
        final String name = ((PsiNamedElement)object).getName();
        if (name != null) {
          return name.hashCode();
        }
      }
      return object.hashCode();
    }

    @Override
    public boolean equals(final PsiElement o1, final PsiElement o2) {
      if (o1 instanceof PsiNamedElement && o2 instanceof PsiNamedElement) {
        return Comparing.equal(((PsiNamedElement)o1).getName(), ((PsiNamedElement)o2).getName());
      }
      return o1.equals(o2);
    }
  };

  @Override
  public Object[] getFileReferenceCompletionVariants(final FileReference reference) {
    final String s = reference.getText();
    if (s != null && s.equals("/")) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    final CommonProcessors.CollectUniquesProcessor<PsiFileSystemItem> collector =
      new CommonProcessors.CollectUniquesProcessor<PsiFileSystemItem>();
    final PsiElementProcessor<PsiFileSystemItem> processor = new PsiElementProcessor<PsiFileSystemItem>() {
      @Override
      public boolean execute(@NotNull PsiFileSystemItem fileSystemItem) {
        return new FilteringProcessor<PsiFileSystemItem>(reference.getFileReferenceSet().getReferenceCompletionFilter(), collector).process(
          FileReference.getOriginalFile(fileSystemItem));
      }
    };
    for (PsiFileSystemItem context : reference.getContexts()) {
      for (final PsiElement child : context.getChildren()) {
        if (child instanceof PsiFileSystemItem) {
          processor.execute((PsiFileSystemItem)child);
        }
      }
    }
    final THashSet<PsiElement> set = new THashSet<PsiElement>(collector.getResults(), VARIANTS_HASHING_STRATEGY);
    final PsiElement[] candidates = PsiUtilCore.toPsiElementArray(set);

    final Object[] variants = new Object[candidates.length];
    for (int i = 0; i < candidates.length; i++) {
      PsiElement candidate = candidates[i];
      Object item = reference.createLookupItem(candidate);
      if (item == null) {
        item = FileInfoManager.getFileLookupItem(candidate);
      }
      variants[i] = item;
    }
    if (!reference.getFileReferenceSet().isUrlEncoded()) {
      return variants;
    }
    List<Object> encodedVariants = new ArrayList<Object>(variants.length);
    for (int i = 0; i < candidates.length; i++) {
      final PsiElement element = candidates[i];
      if (element instanceof PsiNamedElement) {
        final PsiNamedElement psiElement = (PsiNamedElement)element;
        String name = psiElement.getName();
        final String encoded = reference.encode(name, psiElement);
        if (encoded == null) continue;
        if (!encoded.equals(name)) {
          final Icon icon = psiElement.getIcon(Iconable.ICON_FLAG_READ_STATUS | Iconable.ICON_FLAG_VISIBILITY);
          LookupElementBuilder item = FileInfoManager.getFileLookupItem(candidates[i], encoded, icon);
          encodedVariants.add(item.withTailText(" (" + name + ")"));
        }
        else {
          encodedVariants.add(variants[i]);
        }
      }
    }
    return ArrayUtil.toObjectArray(encodedVariants);
  }
}
