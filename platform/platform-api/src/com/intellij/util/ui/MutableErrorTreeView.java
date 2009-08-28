package com.intellij.util.ui;

import com.intellij.ide.errorTreeView.SimpleErrorData;
import com.intellij.ide.errorTreeView.HotfixData;

import java.util.List;

public interface MutableErrorTreeView extends ErrorTreeView {
  void removeGroup(final String name);
  List<Object> getGroupChildrenData(final String groupName);
  void addFixedHotfixGroup(final String text, final List<SimpleErrorData> children);
  void addHotfixGroup(final HotfixData hotfixData, final List<SimpleErrorData> children);
  void reload();
}
