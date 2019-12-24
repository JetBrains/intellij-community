// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ide.errorTreeView.HotfixData;
import com.intellij.ide.errorTreeView.SimpleErrorData;

import java.util.List;

public interface MutableErrorTreeView extends ErrorTreeView {
  void removeGroup(final String name);

  List<Object> getGroupChildrenData(final String groupName);

  void addFixedHotfixGroup(final String text, final List<? extends SimpleErrorData> children);

  void addHotfixGroup(final HotfixData hotfixData, final List<? extends SimpleErrorData> children);

  void reload();
}
