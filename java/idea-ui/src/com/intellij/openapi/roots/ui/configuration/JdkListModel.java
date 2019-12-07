// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.roots.ui.configuration.JdkComboBox.*;

public class JdkListModel {
  private final boolean myIsSearching;
  private final ImmutableList<JdkComboBoxItem> myItems;
  private final ImmutableMap<JdkComboBoxItem, String> mySeparators;

  public JdkListModel(boolean isSearching, @NotNull List<? extends JdkComboBoxItem> items) {
    myIsSearching = isSearching;
    myItems = ImmutableList.copyOf(items);

    boolean myFirstSepSet = false;
    boolean mySuggestedSep = false;
    ImmutableMap.Builder<JdkComboBoxItem, String> sep = ImmutableMap.builder();

    for (JdkComboBoxItem it : myItems) {
      if (!myFirstSepSet && (it instanceof ActionGroupJdkItem || it instanceof ActionJdkItem)) {
        myFirstSepSet = true;
        sep.put(it, "");
      }

      if (!mySuggestedSep && it instanceof SuggestedJdkItem) {
        mySuggestedSep = true;
        sep.put(it, ProjectBundle.message("jdk.combo.box.autodetected"));
      }
    }
    mySeparators = sep.build();
  }

  @NotNull
  public JdkListModel buildSubModel(@NotNull ActionGroupJdkItem group) {
    return new JdkListModel(myIsSearching, group.mySubItems);
  }

  public boolean isSearching() {
    return myIsSearching;
  }

  @NotNull
  public List<JdkComboBoxItem> getItems() {
    return myItems;
  }

  @Nullable
  public String getSeparatorTextAbove(@NotNull JdkComboBoxItem value) {
    return mySeparators.get(value);
  }

  @Nullable
  public ActualJdkComboBoxItem findSdkItem(@NotNull Sdk value) {
    for (JdkComboBoxItem item : myItems) {
      if (!(item instanceof ActualJdkComboBoxItem)) continue;
      ActualJdkComboBoxItem sdkItem = (ActualJdkComboBoxItem)item;
      if (sdkItem.hasSameSdk(value)) {
        return sdkItem;
      }
    }
    return null;
  }
}
