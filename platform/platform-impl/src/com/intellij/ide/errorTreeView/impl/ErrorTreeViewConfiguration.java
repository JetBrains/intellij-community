// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.errorTreeView.impl;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
@State(name = "ErrorTreeViewConfiguration", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE))
public final class ErrorTreeViewConfiguration implements PersistentStateComponent<ErrorTreeViewConfiguration> {
  public boolean IS_AUTOSCROLL_TO_SOURCE = false;
  public boolean HIDE_WARNINGS = false;
  public boolean HIDE_INFO_MESSAGES = false;

  public static ErrorTreeViewConfiguration getInstance(Project project) {
    return project.getService(ErrorTreeViewConfiguration.class);
  }

  public boolean isAutoscrollToSource() {
    return IS_AUTOSCROLL_TO_SOURCE;
  }

  public void setAutoscrollToSource(boolean autoscroll) {
    IS_AUTOSCROLL_TO_SOURCE = autoscroll;
  }

  public boolean isHideWarnings() {
    return HIDE_WARNINGS;
  }

  public void setHideWarnings(boolean value) {
    HIDE_WARNINGS = value;
  }

  public boolean isHideInfoMessages() {
    return HIDE_INFO_MESSAGES;
  }

  public void setHideInfoMessages(boolean value) {
    HIDE_INFO_MESSAGES = value;
  }

  @Override
  public ErrorTreeViewConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull final ErrorTreeViewConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
