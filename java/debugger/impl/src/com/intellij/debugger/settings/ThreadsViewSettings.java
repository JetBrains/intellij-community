// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(name = "ThreadsViewSettings", storages = @Storage("debugger.xml"))
public final class ThreadsViewSettings implements PersistentStateComponent<ThreadsViewSettings> {
  public boolean SHOW_THREAD_GROUPS = false;
  public boolean SHOW_LINE_NUMBER = true;
  public boolean SHOW_CLASS_NAME = true;
  public boolean SHOW_PACKAGE_NAME = true;
  public boolean SHOW_SOURCE_NAME = false;
  public boolean SHOW_SYNTHETIC_FRAMES = true;
  public boolean SHOW_CURRENT_THREAD = true;
  public boolean SHOW_ARGUMENTS_TYPES = false;

  public static ThreadsViewSettings getInstance() {
    return ServiceManager.getService(ThreadsViewSettings.class);
 }

  @Override
  public ThreadsViewSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull final ThreadsViewSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}