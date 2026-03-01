// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PresentableLookupValue;
import com.intellij.diagnostic.PluginException;
import com.intellij.modcompletion.CommonCompletionItem;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.modcompletion.ModCompletionItemProvider;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.paths.PsiDynaReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferencesWrapper;
import com.intellij.psi.ReferenceRange;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A provider of variants from {@link PsiReference#getVariants()}. 
 * Based on {@link LegacyCompletionContributor} and {@link CompletionData}.
 * {@link LookupElement} variants are ignored for now.
 */
@NotNullByDefault
final class PsiReferenceCompletionItemProvider implements ModCompletionItemProvider {
  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    processReferences(context, (reference, prefix) -> {
      final Set<ModCompletionItem> lookupSet = new LinkedHashSet<>();
      completeReference(reference, lookupSet);
      PrefixMatcher matcher = context.matcher().cloneWithPrefix(prefix);
      for (final ModCompletionItem item : lookupSet) {
        if (matcher.prefixMatches(item.mainLookupString()) ||
            ContainerUtil.exists(item.additionalLookupStrings(), matcher::prefixMatches)) {
          sink.accept(item);
        }
      }
    });
  }

  private static void completeReference(PsiReference reference, Set<ModCompletionItem> set) {
    if (reference instanceof PsiMultiReference multiReference) {
      for (PsiReference ref : CompletionData.getReferences(multiReference)) {
        completeReference(ref, set);
      }
    }
    else if (reference instanceof PsiReferencesWrapper wrapper) {
      for (PsiReference ref : wrapper.getReferences()) {
        completeReference(ref, set);
      }
    }
    else{
      @SuppressWarnings("SSBasedInspection") 
      @Nullable Object[] completions = reference.getVariants();
      for (Object completion : completions) {
        ModCompletionItem ret = objectToLookupItem(completion);
        if (ret != null) {
          set.add(ret);
        }
      }
    }
  }

  public static @Nullable ModCompletionItem objectToLookupItem(@Nullable Object object) {
    if (object == null) {
      return null;
    }
    if (object instanceof LookupElement) {
      // Not supported yet
      return null;
    }

    String s = null;
    if (object instanceof PsiElement psiElement){
      s = PsiUtilCore.getName(psiElement);
    }
    else if (object instanceof PsiMetaData metaData) {
      s = metaData.getName();
    }
    else if (object instanceof String string) {
      s = string;
    }
    else if (object instanceof PresentableLookupValue lookupValue) {
      s = lookupValue.getPresentation();
    }
    if (s == null) {
      throw PluginException.createByClass("Null string for object: " + object + " of " + object.getClass(), null, object.getClass());
    }
    
    return new CommonCompletionItem(s).withObject(object);
  }


  public static void processReferences(CompletionContext parameters,
                                       BiConsumer<? super PsiReference, ? super String> consumer) {
    final int startOffset = parameters.getOffset();
    final PsiReference ref = parameters.getPosition().getContainingFile().findReferenceAt(startOffset);
    if (ref instanceof PsiMultiReference multi) {
      for (final PsiReference reference : CompletionData.getReferences(multi)) {
        if (reference instanceof PsiReferencesWrapper wrapper) {
          for (PsiReference r : wrapper.getReferences()) {
            processReference(startOffset, consumer, r);
          }
        }
        else {
          processReference(startOffset, consumer, reference);
        }
      }
    }
    else if (ref instanceof PsiDynaReference<?> dyna) {
      for (final PsiReference reference : dyna.getReferences()) {
        processReference(startOffset, consumer, reference);
      }
    }
    else if (ref != null) {
      processReference(startOffset, consumer, ref);
    }
  }

  private static void processReference(int startOffset,
                                       BiConsumer<? super PsiReference, ? super String> consumer,
                                       PsiReference reference) {
    PsiElement element = reference.getElement();
    final int offsetInElement = startOffset - element.getTextRange().getStartOffset();
    if (!ReferenceRange.containsOffsetInElement(reference, offsetInElement)) {
      return;
    }

    TextRange range = reference.getRangeInElement();
    String prefix = element.getText().substring(range.getStartOffset(), offsetInElement);
    consumer.accept(reference, prefix);
  }
}
