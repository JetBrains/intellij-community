/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.todo;

import com.intellij.util.xmlb.annotations.OptionTag;
import org.jetbrains.annotations.NotNull;

public class TodoPanelSettings {
  @OptionTag(tag = "are-packages-shown", nameAttribute = "")
  public boolean arePackagesShown;
  @OptionTag(tag = "are-modules-shown", nameAttribute = "")
  public boolean areModulesShown;
  @OptionTag(tag = "flatten-packages", nameAttribute = "")
  public boolean areFlattenPackages;
  @OptionTag(tag = "is-autoscroll-to-source", nameAttribute = "")
  public boolean isAutoScrollToSource;
  @OptionTag(tag = "todo-filter", nameAttribute = "", valueAttribute = "name")
  public String todoFilterName;
  @OptionTag(tag = "is-preview-enabled", nameAttribute = "")
  public boolean showPreview;

  public TodoPanelSettings() {
  }

  public TodoPanelSettings(@NotNull TodoPanelSettings s) {
    arePackagesShown = s.arePackagesShown;
    areModulesShown = s.areModulesShown;
    areFlattenPackages = s.areFlattenPackages;
    isAutoScrollToSource = s.isAutoScrollToSource;
    todoFilterName = s.todoFilterName;
    showPreview = s.showPreview;
  }
}
