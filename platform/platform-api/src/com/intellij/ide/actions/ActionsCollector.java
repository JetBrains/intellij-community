// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public abstract class ActionsCollector {
  /**
   * Only actions from platform and JB plugins are recorded.
   * If no context class is provided then nothing will be recorded.
   * @deprecated use {@link #record(String, Class)} instead
   */
  public void record(String actionId) {}

  /**
   * Only actions from platform and JB plugins are recorded.
   */
  public abstract void record(@Nullable String actionId, @NotNull Class context);

  public abstract State getState();

  public static ActionsCollector getInstance() {
    return ServiceManager.getService(ActionsCollector.class);
  }

  public final static class State {
    @Tag("counts")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "action", valueAttributeName = "count")
    public Map<String, Integer> myValues = new HashMap<>();
  }
}
