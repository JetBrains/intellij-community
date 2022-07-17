// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.diagnostic.ActivityCategory;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TopHitCache implements Disposable {
  protected final ConcurrentMap<Class<?>, Collection<OptionDescription>> map = new ConcurrentHashMap<>();

  public static TopHitCache getInstance() {
    return ApplicationManager.getApplication().getService(TopHitCache.class);
  }

  @Override
  public void dispose() {
    clear();
  }

  public void clear() {
    map.values().forEach(TopHitCache::dispose);
    map.clear();
  }

  private static void dispose(Collection<? extends OptionDescription> options) {
    if (options != null) {
      options.forEach(TopHitCache::dispose);
    }
  }

  private static void dispose(OptionDescription option) {
    if (option instanceof Disposable) {
      Disposer.dispose((Disposable)option);
    }
  }

  public final void invalidateCachedOptions(@NotNull Class<? extends OptionsSearchTopHitProvider> providerClass) {
    Collection<OptionDescription> removed = map.remove(providerClass);
    if (removed != null) {
      dispose(removed);
    }
  }

  public final @NotNull Collection<OptionDescription> getCachedOptions(@NotNull OptionsSearchTopHitProvider provider,
                                                                       @Nullable Project project,
                                                                       @Nullable PluginDescriptor pluginDescriptor) {
    return map.computeIfAbsent(provider.getClass(), aClass -> {
      Collection<OptionDescription> result;
      long startTime = StartUpMeasurer.getCurrentTime();
      if (provider instanceof OptionsSearchTopHitProvider.ProjectLevelProvider) {
        //noinspection ConstantConditions
        result = ((OptionsSearchTopHitProvider.ProjectLevelProvider)provider).getOptions(project);
      }
      else if (provider instanceof OptionsSearchTopHitProvider.ApplicationLevelProvider) {
        result = ((OptionsSearchTopHitProvider.ApplicationLevelProvider)provider).getOptions();
      }
      else {
        return Collections.emptyList();
      }

      ActivityCategory category = project == null ? ActivityCategory.APP_OPTIONS_TOP_HIT_PROVIDER : ActivityCategory.PROJECT_OPTIONS_TOP_HIT_PROVIDER;
      StartUpMeasurer.addCompletedActivity(startTime, aClass, category, pluginDescriptor == null ? null : pluginDescriptor.getPluginId().getIdString());
      return result;
    });
  }
}
