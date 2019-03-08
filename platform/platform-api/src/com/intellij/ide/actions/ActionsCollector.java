// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public abstract class ActionsCollector {
  /**
   * @deprecated use {@link #record(Project, AnAction, AnActionEvent)} instead
   */
  @Deprecated
  public void record(@Nullable AnAction action, @Nullable AnActionEvent event) {}

  /**
   * Records explicitly whitelisted actions
   */
  public void record(@Nullable String actionId, @NotNull Class context) {
    record(actionId, null, context);
  }

  /**
   * Records explicitly whitelisted actions with input event
   */
  public abstract void record(@Nullable String actionId, @Nullable InputEvent event, @NotNull Class context);

  /**
   * Records action id for global actions or action class name for actions generated on runtime.
   * Only actions from platform and JB plugins are recorded.
   */
  public abstract void record(@Nullable Project project, @Nullable AnAction action, @Nullable AnActionEvent event);

  public abstract State getState();

  public static ActionsCollector getInstance() {
    return ServiceManager.getService(ActionsCollector.class);
  }

  public final static class State {
    @Tag("counts")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "action", valueAttributeName = "count")
    public Map<String, Integer> myValues = new HashMap<>();

    @Tag("contextMenuCounts")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "action", valueAttributeName = "count")
    public Map<String, Integer> myContextMenuValues = new HashMap<>();
  }
}
