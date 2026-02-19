// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
@ApiStatus.Internal
public final class ScopeOrderComparator implements Comparator<String> {
  private final List<String> myScopesOrder;

  public ScopeOrderComparator(@NotNull InspectionProfileImpl inspectionProfile) {
    this(inspectionProfile.getScopesOrder());
  }

  private ScopeOrderComparator(List<String> scopesOrder) {
    myScopesOrder = scopesOrder;
  }

  private int getKey(String scope) {
    return myScopesOrder == null ? -1 : myScopesOrder.indexOf(scope);
  }

  @Override
  public int compare(String scope1, String scope2) {
    final int key1 = getKey(scope1);
    final int key2 = getKey(scope2);

    if (key1 == -1 && key2 == -1) return scope1.compareTo(scope2);
    return Integer.compare(key1, key2);
  }
}