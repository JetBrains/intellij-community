// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.newStructureView;

import com.intellij.ide.structureView.SearchableTextProvider;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.LocationPresentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class StructureViewUtil {
  public static @Nullable String getSpeedSearchText(Object object) {
    String text = String.valueOf(object);
    Object value = StructureViewComponent.unwrapWrapper(object);
    if (text != null) {
      if (value instanceof SearchableTextProvider searchableTextProvider) {
        String searchableText = searchableTextProvider.getSearchableText();
        if (searchableText != null) return searchableText;
      }
      if (value instanceof PsiTreeElementBase && ((PsiTreeElementBase<?>)value).isSearchInLocationString()) {
        String locationString = ((PsiTreeElementBase<?>)value).getLocationString();
        if (!StringUtil.isEmpty(locationString)) {
          String locationPrefix = null;
          String locationSuffix = null;
          if (value instanceof LocationPresentation) {
            locationPrefix = ((LocationPresentation)value).getLocationPrefix();
            locationSuffix = ((LocationPresentation)value).getLocationSuffix();
          }

          return text +
                 StringUtil.notNullize(locationPrefix, LocationPresentation.DEFAULT_LOCATION_PREFIX) +
                 locationString +
                 StringUtil.notNullize(locationSuffix, LocationPresentation.DEFAULT_LOCATION_SUFFIX);
        }
      }
      return text;
    }
    // NB!: this point is achievable if the following method returns null
    // see com.intellij.ide.util.treeView.NodeDescriptor.toString
    if (value instanceof TreeElement) {
      return ReadAction.compute(() -> {
        if (value instanceof SearchableTextProvider searchableTextProvider) {
          String searchableText = searchableTextProvider.getSearchableText();
          if (searchableText != null) return searchableText;
        }
        return ((TreeElement)value).getPresentation().getPresentableText();
      });
    }

    return null;
  }
}
