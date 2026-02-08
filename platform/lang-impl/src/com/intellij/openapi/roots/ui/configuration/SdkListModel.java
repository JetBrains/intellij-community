// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.ComboBoxPopupState;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractListModel;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.intellij.openapi.roots.ui.configuration.SdkListItem.ActionItem;
import static com.intellij.openapi.roots.ui.configuration.SdkListItem.GroupItem;
import static com.intellij.openapi.roots.ui.configuration.SdkListItem.NoneSdkItem;
import static com.intellij.openapi.roots.ui.configuration.SdkListItem.ProjectSdkItem;
import static com.intellij.openapi.roots.ui.configuration.SdkListItem.SdkItem;
import static com.intellij.openapi.roots.ui.configuration.SdkListItem.SuggestedItem;

public final class SdkListModel extends AbstractListModel<SdkListItem> implements ComboBoxPopupState<SdkListItem> {
  private final boolean myIsSearching;
  private final List<SdkListItem> myItems;
  private final Supplier<? extends Sdk> myGetProjectSdk;
  private final Map<SdkListItem, @NlsContexts.Separator String> mySeparators;

  public static @NotNull SdkListModel emptyModel() {
    return new SdkListModel(false, List.of(), () -> null);
  }

  SdkListModel(boolean isSearching,
               @NotNull List<? extends SdkListItem> items,
               @NotNull Supplier<? extends Sdk> getProjectSdk) {
    myIsSearching = isSearching;
    myItems = List.copyOf(items);
    myGetProjectSdk = getProjectSdk;

    boolean myFirstSepSet = false;
    boolean mySuggestedSep = false;
    ImmutableMap.Builder<SdkListItem, String> sep = ImmutableMap.builder();

    // putting 0 to avoid the first separator
    int lastSepIndex = 0;
    for (int i = 0; i < myItems.size(); i++) {
      SdkListItem it = myItems.get(i);

      if (!myFirstSepSet && (it instanceof GroupItem || it instanceof ActionItem)) {
        myFirstSepSet = true;
        if (lastSepIndex < i) {
          sep.put(it, "");
          lastSepIndex = i;
        }
      }

      if (!mySuggestedSep && it instanceof SuggestedItem) {
        mySuggestedSep = true;
        if (lastSepIndex < i) {
          sep.put(it, ProjectBundle.message("jdk.combo.box.autodetected"));
          lastSepIndex = i;
        }
      }
    }
    mySeparators = sep.build();
  }

  public @Nullable SdkListItem findProjectSdkItem() {
    return findFirstItemOfType(ProjectSdkItem.class);
  }

  public @Nullable SdkListItem findNoneSdkItem() {
    return findFirstItemOfType(NoneSdkItem.class);
  }

  private @Nullable SdkListItem findFirstItemOfType(Class<? extends SdkListItem> itemClass) {
    return ContainerUtil.find(getItems(), itemClass::isInstance);
  }

  @Nullable
  Sdk resolveProjectSdk() {
    return myGetProjectSdk.get();
  }

  @Override
  public int getSize() {
    return myItems.size();
  }

  @Override
  public @NotNull SdkListItem getElementAt(int index) {
    return myItems.get(index);
  }

  @Override
  public @Nullable SdkListModel onChosen(SdkListItem selectedValue) {
    if (!(selectedValue instanceof GroupItem)) return null;
    return new SdkListModel(myIsSearching, ((GroupItem)selectedValue).subItems, myGetProjectSdk);
  }

  @Override
  public boolean hasSubstep(SdkListItem selectedValue) {
    return selectedValue instanceof GroupItem;
  }

  public boolean isSearching() {
    return myIsSearching;
  }

  public @NotNull List<SdkListItem> getItems() {
    return myItems;
  }

  public @Nullable @NlsContexts.Separator String getSeparatorTextAbove(@NotNull SdkListItem value) {
    return mySeparators.get(value);
  }

  public @Nullable SdkItem findSdkItem(@Nullable Sdk value) {
    if (value == null) return null;
    for (SdkListItem item : myItems) {
      if (!(item instanceof SdkItem sdkItem)) continue;
      if (sdkItem.hasSameSdk(value)) {
        return sdkItem;
      }
    }
    return null;
  }

  public @Nullable SdkItem findSdkItem(@Nullable String sdkName) {
    if (sdkName == null) return null;
    for (SdkListItem item : myItems) {
      if (!(item instanceof SdkItem sdkItem)) continue;
      Sdk sdk = sdkItem.sdk;
      if (sdkName.equals(sdk.getName())) {
        return sdkItem;
      }
    }
    return null;
  }
}
