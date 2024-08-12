// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.daemon.impl.LineMarkersPass;
import com.intellij.lang.Language;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class RelatedItemLineMarkerGotoAdapter extends GotoRelatedProvider {
  @Override
  public @NotNull List<? extends GotoRelatedItem> getItems(@NotNull PsiElement context) {
    List<PsiElement> parents = new ArrayList<>();
    PsiElement current = context;
    Set<Language> languages = new HashSet<>();
    while (current != null) {
      parents.add(current);
      languages.add(current.getLanguage());
      if (current instanceof PsiFile) break;
      current = current.getParent();
    }

    List<LineMarkerProvider> providers = new ArrayList<>();
    for (Language language : languages) {
      providers.addAll(LineMarkersPass.getMarkerProviders(language, context.getProject()));
    }
    List<GotoRelatedItem> items = new ArrayList<>();
    for (LineMarkerProvider provider : providers) {
      if (provider instanceof RelatedItemLineMarkerProvider relatedItemLineMarkerProvider) {
        List<RelatedItemLineMarkerInfo<?>> markers = new ArrayList<>();
        for (PsiElement parent : parents) {
          ContainerUtil.addIfNotNull(markers, relatedItemLineMarkerProvider.getLineMarkerInfo(parent));
        }
        relatedItemLineMarkerProvider.collectNavigationMarkers(parents, markers, true);

        addItemsForMarkers(markers, items);
      }
    }

    return items;
  }

  private static void addItemsForMarkers(List<? extends RelatedItemLineMarkerInfo> markers,
                                         List<? super GotoRelatedItem> result) {
    Set<PsiFile> addedFiles = new HashSet<>();
    for (RelatedItemLineMarkerInfo<?> marker : markers) {
      Collection<? extends GotoRelatedItem> items = marker.createGotoRelatedItems();
      for (GotoRelatedItem item : items) {
        PsiElement element = item.getElement();
        if (element instanceof PsiFile file) {
          if (addedFiles.contains(file)) {
            continue;
          }
        }
        if (element != null) {
          ContainerUtil.addIfNotNull(addedFiles, element.getContainingFile());
        }
        result.add(item);
      }
    }
  }
}
