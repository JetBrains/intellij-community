// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.options.SchemeManagerFactory;
import com.intellij.openapi.options.SchemeProcessor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class BaseToolManager<T extends Tool> implements Disposable {
  private final SchemeManagerFactory myFactory;
  private final SchemeManager<ToolsGroup<T>> mySchemeManager;

  public BaseToolManager(@NotNull SchemeManagerFactory factory, @NotNull String schemePath, @NotNull String presentableName) {
    myFactory = factory;
    //noinspection AbstractMethodCallInConstructor
    mySchemeManager =
      factory.create(schemePath, createProcessor(), presentableName, RoamingType.DEFAULT, name -> FileUtil.sanitizeFileName(name, false),
                     null, null, true, SettingsCategory.OTHER);
    mySchemeManager.loadSchemes();
  }

  protected abstract SchemeProcessor<ToolsGroup<T>, ToolsGroup<T>> createProcessor();

  protected @Nullable ActionManagerEx getActionManager() {
    return ActionManagerEx.getInstanceEx();
  }

  public static @Nullable String convertString(String s) {
    return StringUtil.nullize(s, true);
  }

  public List<T> getTools() {
    List<T> result = new SmartList<>();
    for (ToolsGroup<T> group : mySchemeManager.getAllSchemes()) {
      result.addAll(group.getElements());
    }
    return result;
  }

  public @NotNull List<T> getTools(@NotNull String group) {
    ToolsGroup<T> groupByName = mySchemeManager.findSchemeByName(group);
    if (groupByName == null) {
      return Collections.emptyList();
    }
    else {
      return groupByName.getElements();
    }
  }

  public List<ToolsGroup<T>> getGroups() {
    return mySchemeManager.getAllSchemes();
  }

  public void setTools(@NotNull List<ToolsGroup<T>> tools) {
    mySchemeManager.setSchemes(tools);
    ActionManagerEx actionManager = getActionManager();
    if (actionManager != null) {
      registerActions(actionManager.asActionRuntimeRegistrar());
    }
  }

  /**
   * @deprecated Use {@link #registerActions(ActionRuntimeRegistrar)}
   */
  @Deprecated
  protected final void registerActions(@Nullable ActionManager actionManager) {
    if (actionManager instanceof ActionManagerEx managerEx) {
      registerActions(managerEx.asActionRuntimeRegistrar());
    }
  }

  protected final void registerActions(@NotNull ActionRuntimeRegistrar actionRegistrar) {
    unregisterActions(actionRegistrar);

    // register
    // to prevent exception if 2 or more targets have the same name
    Set<String> registeredIds = new HashSet<>();
    for (T tool : getTools()) {
      String actionId = tool.getActionId();
      if (registeredIds.add(actionId)) {
        actionRegistrar.registerAction(actionId, createToolAction(tool));
      }
    }
  }

  protected @NotNull ToolAction createToolAction(@NotNull T tool) {
    return new ToolAction(tool);
  }

  protected abstract String getActionIdPrefix();

  protected void unregisterActions(@NotNull ActionRuntimeRegistrar actionManager) {
    actionManager.unregisterActionByIdPrefix(getActionIdPrefix());
  }

  @Override
  public void dispose() {
    myFactory.dispose(mySchemeManager);
  }
}
