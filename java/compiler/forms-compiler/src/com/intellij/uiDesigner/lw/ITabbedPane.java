// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.lw;

public interface ITabbedPane extends IContainer {
  String TAB_TITLE_PROPERTY = "Tab Title";
  String TAB_TOOLTIP_PROPERTY = "Tab Tooltip";

  StringDescriptor getTabProperty(IComponent component, final String propName);
}
