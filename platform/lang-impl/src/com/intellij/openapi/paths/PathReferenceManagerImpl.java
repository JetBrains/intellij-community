// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.paths;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public final class PathReferenceManagerImpl extends PathReferenceManager {
  private final StaticPathReferenceProvider myStaticProvider = new StaticPathReferenceProvider(null);
  private final PathReferenceProvider myGlobalPathsProvider = new GlobalPathReferenceProvider();

  @Override
  public @Nullable PathReference getPathReference(@NotNull String path,
                                                  @NotNull PsiElement element,
                                                  PathReferenceProvider... additionalProviders) {
    PathReference pathReference;
    for (PathReferenceProvider provider : getProviders()) {
      pathReference = provider.getPathReference(path, element);
      if (pathReference != null) {
        return pathReference;
      }
    }
    for (PathReferenceProvider provider : additionalProviders) {
      pathReference = provider.getPathReference(path, element);
      if (pathReference != null) {
        return pathReference;
      }
    }
    pathReference = myStaticProvider.getPathReference(path, element);
    if (pathReference != null) {
      return pathReference;
    }
    return null;
  }

  @Override
  public @Nullable PathReference getCustomPathReference(@NotNull String path,
                                                        @NotNull Module module,
                                                        @NotNull PsiElement element,
                                                        PathReferenceProvider... providers) {
    for (PathReferenceProvider provider : providers) {
      PathReference reference = provider.getPathReference(path, element);
      if (reference != null) {
        return reference;
      }
    }
    return null;
  }

  @Override
  public @NotNull PathReferenceProvider getGlobalWebPathReferenceProvider() {
    return myGlobalPathsProvider;
  }

  @Override
  public @NotNull PathReferenceProvider createStaticPathReferenceProvider(final boolean relativePathsAllowed) {
    final StaticPathReferenceProvider provider = new StaticPathReferenceProvider(null);
    provider.setRelativePathsAllowed(relativePathsAllowed);
    return provider;
  }

  @Override
  public PsiReference @NotNull [] createReferences(final @NotNull PsiElement psiElement,
                                                   final boolean soft,
                                                   boolean endingSlashNotAllowed,
                                                   final boolean relativePathsAllowed, PathReferenceProvider... additionalProviders) {
    return createReferences(psiElement, soft, endingSlashNotAllowed, relativePathsAllowed, null, additionalProviders);
  }

  @Override
  public PsiReference @NotNull [] createReferences(final @NotNull PsiElement psiElement,
                                                   final boolean soft,
                                                   boolean endingSlashNotAllowed,
                                                   final boolean relativePathsAllowed,
                                                   FileType[] suitableFileTypes,
                                                   PathReferenceProvider... additionalProviders) {
    List<PsiReference> references = new ArrayList<>();

    processProvider(psiElement, myGlobalPathsProvider, references, soft);
    if (!references.isEmpty()) return references.toArray(PsiReference.EMPTY_ARRAY);  // references like https://www.jetbrains.com/idea/

    StaticPathReferenceProvider staticProvider = new StaticPathReferenceProvider(suitableFileTypes);
    staticProvider.setEndingSlashNotAllowed(endingSlashNotAllowed);
    staticProvider.setRelativePathsAllowed(relativePathsAllowed);
    processProvider(psiElement, staticProvider, references, soft);

    for (PathReferenceProvider provider : getProviders()) {
      processProvider(psiElement, provider, references, soft);
    }
    for (PathReferenceProvider provider : additionalProviders) {
      processProvider(psiElement, provider, references, soft);
    }
    for (PathReferenceProvider provider : ANCHOR_REFERENCE_PROVIDER_EP.getExtensionList()) {
      processProvider(psiElement, provider, references, soft);
    }
    return doMerge(psiElement, references);
  }

  private static PsiReference[] doMerge(final PsiElement element, final List<? extends PsiReference> references) {
    Map<TextRange, PsiReference> byTextRanges = new LinkedHashMap<>();

    for (PsiReference reference : references) {
      final TextRange textRange = reference.getRangeInElement();
      final PsiReference psiReference = byTextRanges.get(textRange);
      if (psiReference == null) {
        byTextRanges.put(textRange, reference);
      } else if (psiReference instanceof PsiDynaReference) {
        ((PsiDynaReference<?>)psiReference).addReference(reference);
      } else {
        byTextRanges.put(textRange, createDynaReference(element, textRange, reference, psiReference));
      }
    }

    return byTextRanges.values().toArray(PsiReference.EMPTY_ARRAY);
  }

  @Override
  public PsiReference @NotNull [] createCustomReferences(@NotNull PsiElement psiElement, boolean soft, PathReferenceProvider... providers) {
    List<PsiReference> references = new ArrayList<>();
    for (PathReferenceProvider provider : providers) {
      boolean processed = processProvider(psiElement, provider, references, soft);
      if (processed) {
        break;
      }
    }
    return references.toArray(PsiReference.EMPTY_ARRAY);
  }

  @Override
  public PsiReference @NotNull [] createReferences(@NotNull PsiElement psiElement,
                                                   final boolean soft,
                                                   PathReferenceProvider... additionalProviders) {
    return createReferences(psiElement, soft, false, true, null, additionalProviders);
  }

  private static @NotNull PsiDynaReference<PsiElement> createDynaReference(@NotNull PsiElement element,
                                                                           @NotNull TextRange textRange,
                                                                           PsiReference... psiReference) {
    final PsiDynaReference<PsiElement> dynaReference = new PsiDynaReference<>(element);
    dynaReference.setRangeInElement(textRange);
    for (PsiReference reference : psiReference) {
      dynaReference.addReference(reference);
    }
    return dynaReference;
  }

  private static boolean processProvider(PsiElement psiElement,
                                         PathReferenceProvider provider,
                                         List<PsiReference> mergedReferences,
                                         boolean soft) {
    return provider.createReferences(psiElement, mergedReferences, soft);
  }

  private static List<PathReferenceProvider> getProviders() {
    return PATH_REFERENCE_PROVIDER_EP.getExtensionList();
  }
}
