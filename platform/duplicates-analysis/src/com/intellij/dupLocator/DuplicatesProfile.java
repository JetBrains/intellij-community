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

package com.intellij.dupLocator;

import com.intellij.dupLocator.treeHash.FragmentsCollector;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class DuplicatesProfile {
  public static final ExtensionPointName<DuplicatesProfile> EP_NAME = ExtensionPointName.create("com.intellij.duplicates.profile");

  @NotNull
  public abstract DuplocateVisitor createVisitor(@NotNull FragmentsCollector collector);

  @NotNull
  public DuplocateVisitor createVisitor(@NotNull FragmentsCollector collector, boolean forIndexing) {
    return createVisitor(collector);
  }

  public abstract boolean isMyLanguage(@NotNull Language language);

  @NotNull
  public abstract DuplocatorState getDuplocatorState(@NotNull Language language);

  public @Nullable @Nls String getComment(@NotNull DupInfo info, int index) {
    return null;
  }

  public boolean isMyDuplicate(@NotNull DupInfo info, int index) {
    PsiFragment[] fragments = info.getFragmentOccurences(index);
    Language language = fragments.length > 0 ? fragments[0].getLanguage() : null;
    return language != null && isMyLanguage(language);
  }

  public boolean supportIndex() {
    return true;
  }

  public boolean supportDuplicatesIndex() {
    return false;
  }

  public boolean acceptsContentForIndexing(FileContent fileContent) {
    return true;
  }

  private static final int FACTOR = 2;
  private static final int MAX_COST = 7000;

  public boolean shouldPutInIndex(PsiFragment fragment, int cost, DuplocatorState state) {
    final int lowerBound = state.getLowerBound();
    if (cost < FACTOR*lowerBound || cost > MAX_COST) {
      return false;
    }

    return true;
  }

  @Nullable
  public static DuplicatesProfile findProfileForLanguage(@NotNull Language language) {
    return findProfileForLanguage(EP_NAME.getExtensionList(), language);
  }

  @NotNull
  public static List<DuplicatesProfile> getAllProfiles() {
    return EP_NAME.getExtensionList();
  }

  @Nullable
  public static DuplicatesProfile findProfileForLanguage(List<? extends DuplicatesProfile> profiles, @NotNull Language language) {
    for (DuplicatesProfile profile : profiles) {
      if (profile.isMyLanguage(language)) {
        return profile;
      }
    }

    return null;
  }

  @Nullable
  public static DuplicatesProfile findProfileForDuplicate(@NotNull DupInfo dupInfo, int index) {
    for (DuplicatesProfile profile : EP_NAME.getExtensionList()) {
      if (profile.isMyDuplicate(dupInfo, index)) {
        return profile;
      }
    }
    return null;
  }

  @NotNull
  public Language getLanguage(@NotNull PsiElement element) {
    return element.getLanguage();
  }

  @Nullable
  public PsiElementRole getRole(@NotNull PsiElement element) {
    return null;
  }
}
