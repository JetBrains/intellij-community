// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.CommonProcessors;
import com.intellij.util.FilteringProcessor;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class FileReferenceCompletionImpl extends FileReferenceCompletion {
  private static final Hash.Strategy<PsiElement> VARIANTS_HASHING_STRATEGY = new Hash.Strategy<>() {
    @Override
    public int hashCode(@Nullable PsiElement object) {
      if (object instanceof PsiNamedElement) {
        String name = ((PsiNamedElement)object).getName();
        if (name != null) {
          return name.hashCode();
        }
      }
      return Objects.hashCode(object);
    }

    @Override
    public boolean equals(@Nullable PsiElement o1, @Nullable PsiElement o2) {
      if (o1 instanceof PsiNamedElement && o2 instanceof PsiNamedElement) {
        return Objects.equals(((PsiNamedElement)o1).getName(), ((PsiNamedElement)o2).getName());
      }
      return Objects.equals(o1, o2);
    }
  };

  @Override
  public Object @NotNull [] getFileReferenceCompletionVariants(final FileReference reference) {
    final String s = reference.getText();
    if (s != null && s.equals("/")) {
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    final CommonProcessors.CollectUniquesProcessor<PsiFileSystemItem> collector =
      new CommonProcessors.CollectUniquesProcessor<>();
    final PsiElementProcessor<PsiFileSystemItem> processor = new PsiElementProcessor<>() {
      @Override
      public boolean execute(@NotNull PsiFileSystemItem fileSystemItem) {
        return new FilteringProcessor<>(reference.getFileReferenceSet().getReferenceCompletionFilter(), collector).process(
          FileReference.getOriginalFile(fileSystemItem));
      }
    };

    List<Object> additionalItems = new ArrayList<>();
    for (PsiFileSystemItem context : reference.getContexts()) {
      for (final PsiElement child : context.getChildren()) {
        if (child instanceof PsiFileSystemItem) {
          processor.execute((PsiFileSystemItem)child);
        }
      }
      if (context instanceof FileReferenceResolver) {
        additionalItems.addAll(((FileReferenceResolver)context).getVariants(reference));
      }
    }

    final FileType[] types = reference.getFileReferenceSet().getSuitableFileTypes();
    final Set<PsiElement> set = new ObjectOpenCustomHashSet<>(collector.getResults(), VARIANTS_HASHING_STRATEGY);
    final PsiElement[] candidates = PsiUtilCore.toPsiElementArray(set);

    final Object[] variants = new Object[candidates.length + additionalItems.size()];
    for (int i = 0; i < candidates.length; i++) {
      PsiElement candidate = candidates[i];
      Object item = reference.createLookupItem(candidate);
      if (item == null) {
        item = FileInfoManager.getFileLookupItem(candidate);
      }
      if (candidate instanceof PsiFile && item instanceof LookupElement &&
          types.length > 0 && ArrayUtil.contains(((PsiFile)candidate).getFileType(), types)) {
        item = PrioritizedLookupElement.withPriority((LookupElement)item, Double.MAX_VALUE);
      }
      variants[i] = item;
    }

    for (int i = 0; i < additionalItems.size(); i++) {
      variants[i + candidates.length] = additionalItems.get(i);
    }
    if (!reference.getFileReferenceSet().isUrlEncoded()) {
      return variants;
    }
    List<Object> encodedVariants = new ArrayList<>(variants.length + additionalItems.size());
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
    encodedVariants.addAll(additionalItems);
    return ArrayUtil.toObjectArray(encodedVariants);
  }
}
