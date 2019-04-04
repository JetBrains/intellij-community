// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author egor
 */
@State(name = "DebuggerSettings", storages = @Storage("debugger.xml"))
public class DebuggerProjectSettings implements PersistentStateComponent<DebuggerProjectSettings> {
  @XCollection(propertyElementName = "async-schedule-annotations", elementName = "annotation", valueAttributeName = "name")
  public String[] myAsyncScheduleAnnotations = ArrayUtil.EMPTY_STRING_ARRAY;

  @XCollection(propertyElementName = "async-execute-annotations", elementName = "annotation", valueAttributeName = "name")
  public String[] myAsyncExecuteAnnotations = ArrayUtil.EMPTY_STRING_ARRAY;

  public static DebuggerProjectSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, DebuggerProjectSettings.class);
  }

  @Nullable
  @Override
  public DebuggerProjectSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull DebuggerProjectSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
