// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Allows to extend information in fus logs about shown lookup.
 * <p>
 * Each update in values we sent should be reflected in white-list scheme for `finished` event in `completion` group.
 * <p>
 * see {@link LookupUsageTracker}
 */
@ApiStatus.Internal
public interface LookupUsageDescriptor {
  ExtensionPointName<LookupUsageDescriptor> EP_NAME = ExtensionPointName.create("com.intellij.lookup.usageDetails");

  /**
   * @return key of extension inside {@link FeatureUsageData} of `completion.finished` event
   */
  @NotNull
  String getExtensionKey();

  /**
   * @deprecated use {@link LookupUsageDescriptor#getAdditionalUsageData(LookupResultDescriptor)}
   */
  @Deprecated(forRemoval = true)
  default void fillUsageData(@NotNull Lookup lookup, @NotNull FeatureUsageData usageData) {
    LookupResultDescriptor lookupResultDescriptor = new LookupResultDescriptor() {
      @Override
      public @NotNull Lookup getLookup() {
        return lookup;
      }

      @Override
      public @Nullable LookupElement getSelectedItem() {
        return null;
      }

      @Override
      public LookupUsageTracker.FinishType getFinishType() {
        return null;
      }
    };
    getAdditionalUsageData(lookupResultDescriptor).forEach(pair -> pair.addData(usageData));
  }

  /**
   * The method is triggered after the lookup usage finishes. Use it to fill usageData with information to collect.
   */
  default List<EventPair<?>> getAdditionalUsageData(@NotNull LookupResultDescriptor lookupResultDescriptor) {
    return Collections.emptyList();
  }
}
