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

package com.intellij.openapi.paths;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class PathReferenceManagerImpl extends PathReferenceManager {
  private final StaticPathReferenceProvider myStaticProvider = new StaticPathReferenceProvider(null);
  private final PathReferenceProvider myGlobalPathsProvider = new GlobalPathReferenceProvider();
  private static final Comparator<PsiReference> START_OFFSET_COMPARATOR =
    (o1, o2) -> o1.getRangeInElement().getStartOffset() - o2.getRangeInElement().getStartOffset();

  @Override
  @Nullable
  public PathReference getPathReference(@NotNull String path,
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
  @Nullable
  public PathReference getCustomPathReference(@NotNull String path, @NotNull Module module, @NotNull PsiElement element, PathReferenceProvider... providers) {
    for (PathReferenceProvider provider : providers) {
      PathReference reference = provider.getPathReference(path, element);
      if (reference != null) {
        return reference;
      }
    }
    return null;
  }

  @Override
  @NotNull
  public PathReferenceProvider getGlobalWebPathReferenceProvider() {
    return myGlobalPathsProvider;
  }

  @Override
  @NotNull
  public PathReferenceProvider createStaticPathReferenceProvider(final boolean relativePathsAllowed) {
    final StaticPathReferenceProvider provider = new StaticPathReferenceProvider(null);
    provider.setRelativePathsAllowed(relativePathsAllowed);
    return provider;
  }

  @Override
  @NotNull
  public PsiReference[] createReferences(@NotNull final PsiElement psiElement,
                                         final boolean soft,
                                         boolean endingSlashNotAllowed,
                                         final boolean relativePathsAllowed, PathReferenceProvider... additionalProviders) {
    return createReferences(psiElement, soft, endingSlashNotAllowed, relativePathsAllowed, null, additionalProviders);
  }

  @Override
  @NotNull
  public PsiReference[] createReferences(@NotNull final PsiElement psiElement,
                                         final boolean soft,
                                         boolean endingSlashNotAllowed,
                                         final boolean relativePathsAllowed, FileType[] suitableFileTypes, PathReferenceProvider... additionalProviders) {

    List<PsiReference> mergedReferences = new ArrayList<>();
    processProvider(psiElement, myGlobalPathsProvider, mergedReferences, soft);

    StaticPathReferenceProvider staticProvider = new StaticPathReferenceProvider(suitableFileTypes);
    staticProvider.setEndingSlashNotAllowed(endingSlashNotAllowed);
    staticProvider.setRelativePathsAllowed(relativePathsAllowed);
    processProvider(psiElement, staticProvider, mergedReferences, soft);

    for (PathReferenceProvider provider : getProviders()) {
      processProvider(psiElement, provider, mergedReferences, soft);
    }
    for (PathReferenceProvider provider : additionalProviders) {
      processProvider(psiElement, provider, mergedReferences, soft);
    }
    for (PathReferenceProvider provider : Extensions.getExtensions(ANCHOR_REFERENCE_PROVIDER_EP)) {
      processProvider(psiElement, provider, mergedReferences, soft);
    }
    return mergeReferences(psiElement, mergedReferences);
  }

  @Override
  @NotNull
  public PsiReference[] createCustomReferences(@NotNull PsiElement psiElement, boolean soft, PathReferenceProvider... providers) {
    List<PsiReference> references = new ArrayList<>();
    for (PathReferenceProvider provider : providers) {
      boolean processed = processProvider(psiElement, provider, references, soft);
      if (processed) {
        break;
      }
    }
    return mergeReferences(psiElement, references);
  }

  @Override
  @NotNull
  public PsiReference[] createReferences(@NotNull PsiElement psiElement, final boolean soft, PathReferenceProvider... additionalProviders) {
    return createReferences(psiElement, soft, false, true, null, additionalProviders);
  }

  private static PsiReference[] mergeReferences(PsiElement element, List<PsiReference> references) {
    if (references.size() <= 1) {
      return references.toArray(new PsiReference[references.size()]);
    }
    Collections.sort(references, START_OFFSET_COMPARATOR);
    final List<PsiReference> intersecting = new ArrayList<>();
    final List<PsiReference> notIntersecting = new ArrayList<>();
    TextRange intersectingRange = references.get(0).getRangeInElement();
    boolean intersected = false;
    for (int i = 1; i < references.size(); i++) {
      final PsiReference reference = references.get(i);
      final TextRange range = reference.getRangeInElement();
      final int offset = range.getStartOffset();
      if (intersectingRange.getStartOffset() <= offset && intersectingRange.getEndOffset() >= offset) {
        intersected = true;
        intersecting.add(references.get(i - 1));
        if (i == references.size() - 1) {
          intersecting.add(reference);
        }
        intersectingRange = intersectingRange.union(range);
      } else {
        if (intersected) {
          intersecting.add(references.get(i - 1));
          intersected = false;
        } else {
          notIntersecting.add(references.get(i - 1));
        }
        intersectingRange = range;
        if (i == references.size() - 1) {
          notIntersecting.add(reference);
        }
      }
    }

    List<PsiReference> result = doMerge(element, intersecting);
    result.addAll(notIntersecting);

    return result.toArray(new PsiReference[result.size()]);
  }

  private static List<PsiReference> doMerge(final PsiElement element, final List<PsiReference> references) {
    List<PsiReference> resolvingRefs = new ArrayList<>();
    List<PsiReference> nonResolvingRefs = new ArrayList<>();

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < references.size(); i++) {
      PsiReference reference = references.get(i);

      assert element.equals(reference.getElement());
      if (reference.resolve() != null) {
        resolvingRefs.add(reference);
      } else {
        nonResolvingRefs.add(reference);
      }
    }

    List<PsiReference> result = new ArrayList<>(5);
    while (!resolvingRefs.isEmpty()) {
      final List<PsiReference> list = new ArrayList<>(5);
      final TextRange range = getFirstIntersectingReferences(resolvingRefs, list);
      final TextRange textRange = addIntersectingReferences(nonResolvingRefs, list, range);
      addToResult(element, result, list, textRange);
    }

    while (!nonResolvingRefs.isEmpty()) {
      final SmartList<PsiReference> list = new SmartList<>();
      final TextRange range = getFirstIntersectingReferences(nonResolvingRefs, list);
      int endOffset = range.getEndOffset();
      for (final PsiReference reference : list) {
        endOffset = Math.min(endOffset, reference.getRangeInElement().getEndOffset());
      }
      addToResult(element, result, list, new TextRange(range.getStartOffset(), endOffset));
    }
    return result;
  }

  private static void addToResult(final PsiElement element,
                                  final List<PsiReference> result,
                                  final List<PsiReference> list,
                                  final TextRange range) {
    if (list.size() == 1) {
      result.add(list.get(0));
    } else {
      final PsiDynaReference psiDynaReference = new PsiDynaReference<>(element);
      psiDynaReference.addReferences(list);
      psiDynaReference.setRangeInElement(range);
      result.add(psiDynaReference);
    }
  }

  private static TextRange addIntersectingReferences(List<PsiReference> set, List<PsiReference> toAdd, TextRange range) {
    int startOffset = range.getStartOffset();
    int endOffset = range.getStartOffset();
    for (Iterator<PsiReference> iterator = set.iterator(); iterator.hasNext();) {
      PsiReference reference = iterator.next();
      final TextRange rangeInElement = reference.getRangeInElement();
      if (intersect(range, rangeInElement)) {
        toAdd.add(reference);
        iterator.remove();
        startOffset = Math.min(startOffset, rangeInElement.getStartOffset());
        endOffset = Math.max(endOffset, rangeInElement.getEndOffset());
      }
    }
    return new TextRange(startOffset, endOffset);
  }

  private static boolean intersect(final TextRange range1, final TextRange range2) {
    return range2.intersectsStrict(range1) || range2.intersects(range1) && (range1.isEmpty() || range2.isEmpty());
  }

  private static TextRange getFirstIntersectingReferences(List<PsiReference> set, List<PsiReference> toAdd) {
    int startOffset = Integer.MAX_VALUE;
    int endOffset = -1;
    for (Iterator<PsiReference> it = set.iterator(); it.hasNext();) {
      PsiReference reference = it.next();
      final TextRange range = reference.getRangeInElement();
      if (endOffset == -1 || range.getStartOffset() <= endOffset) {
        startOffset = Math.min(startOffset, range.getStartOffset());
        endOffset = Math.max(range.getEndOffset(), endOffset);
        toAdd.add(reference);
        it.remove();
      }
      else {
        break;
      }
    }
    return new TextRange(startOffset, endOffset);
  }

  private static boolean processProvider(PsiElement psiElement, PathReferenceProvider provider, List<PsiReference> mergedReferences, boolean soft) {
    return provider.createReferences(psiElement, mergedReferences, soft);
  }

  private static PathReferenceProvider[] getProviders() {
    return Extensions.getExtensions(PATH_REFERENCE_PROVIDER_EP);
  }
}
