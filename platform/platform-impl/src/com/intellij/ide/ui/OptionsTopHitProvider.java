// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.diagnostic.ActivityCategory;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.StartUpPerformanceService;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.WordPrefixMatcher;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public abstract class OptionsTopHitProvider implements OptionsSearchTopHitProvider, SearchTopHitProvider {
  // project level here means not that EP itself in project area, but that extensions applicable for project only
  @VisibleForTesting
  public static final ExtensionPointName<OptionsSearchTopHitProvider.ProjectLevelProvider>
    PROJECT_LEVEL_EP = new ExtensionPointName<>("com.intellij.search.projectOptionsTopHitProvider");

  /**
   * @deprecated Use {@link OptionsSearchTopHitProvider.ApplicationLevelProvider} or {@link OptionsSearchTopHitProvider.ProjectLevelProvider}
   * <p>
   * ConfigurableOptionsTopHitProvider will be refactored later.
   */
  @NotNull
  @Deprecated
  public abstract Collection<OptionDescription> getOptions(@Nullable Project project);

  @NotNull
  private static Collection<OptionDescription> getCachedOptions(@NotNull OptionsSearchTopHitProvider provider,
                                                                @Nullable Project project,
                                                                @Nullable PluginDescriptor pluginDescriptor) {
    ComponentManager manager =
      project == null || provider instanceof ApplicationLevelProvider ? ApplicationManager.getApplication() : project;
    if (manager == null || manager.isDisposed()) {
      return Collections.emptyList();
    }

    CachedOptions cache = manager.getUserData(CachedOptions.KEY);
    if (cache == null) {
      cache = new CachedOptions(manager);
    }

    Class<?> clazz = provider.getClass();
    Collection<OptionDescription> result = cache.map.get(clazz);
    if (result != null) {
      return result;
    }

    long startTime = StartUpMeasurer.getCurrentTime();
    if (provider instanceof ProjectLevelProvider) {
      //noinspection ConstantConditions
      result = ((ProjectLevelProvider)provider).getOptions(project);
    }
    else if (provider instanceof ApplicationLevelProvider) {
      result = ((ApplicationLevelProvider)provider).getOptions();
    }
    else {
      result = ((OptionsTopHitProvider)provider).getOptions(project);
    }
    ActivityCategory category = project == null ? ActivityCategory.APP_OPTIONS_TOP_HIT_PROVIDER : ActivityCategory.PROJECT_OPTIONS_TOP_HIT_PROVIDER;
    StartUpMeasurer.addCompletedActivity(startTime, clazz, category, pluginDescriptor == null ? null : pluginDescriptor.getPluginId().getIdString());
    Collection<OptionDescription> prevValue = cache.map.putIfAbsent(clazz, result);
    return prevValue == null ? result : prevValue;
  }

  @Override
  public final void consumeTopHits(@NotNull String pattern, @NotNull Consumer<Object> collector, @Nullable Project project) {
    consumeTopHits(this, pattern, collector, project);
  }

  static void consumeTopHits(@NotNull OptionsSearchTopHitProvider provider,
                             @NotNull String pattern,
                             @NotNull Consumer<Object> collector,
                             @Nullable Project project) {
    pattern = checkPattern(pattern);
    if (pattern == null) {
      return;
    }

    List<String> parts = StringUtil.split(pattern, " ");
    if (!parts.isEmpty()) {
      doConsumeTopHits(provider, pattern, parts.get(0), collector, project);
    }
  }

  private static void doConsumeTopHits(@NotNull OptionsSearchTopHitProvider provider,
                                       @NotNull String pattern,
                                       @NotNull String id,
                                       @NotNull Consumer<Object> collector,
                                       @Nullable Project project) {
    if (provider.getId().startsWith(id) || pattern.startsWith(" ")) {
      pattern = pattern.startsWith(" ") ? pattern.trim() : pattern.substring(id.length()).trim();
      consumeTopHitsForApplicableProvider(provider, new WordPrefixMatcher(pattern), collector, project);
    }
  }

  private static void consumeTopHitsForApplicableProvider(@NotNull OptionsSearchTopHitProvider provider,
                                                          @NotNull Matcher matcher,
                                                          @NotNull Consumer<Object> collector,
                                                          @Nullable Project project) {
    for (OptionDescription option : getCachedOptions(provider, project, null)) {
      if (matcher.matches(option.getOption())) {
        collector.accept(option);
      }
    }
  }

  @Nullable
  private static String checkPattern(@NotNull String pattern) {
    if (!pattern.startsWith(SearchTopHitProvider.getTopHitAccelerator())) {
      return null;
    }

    pattern = pattern.substring(1);
    return pattern;
  }

  @Override
  @NotNull
  public abstract String getId();

  public static String messageApp(String property) {
    return StringUtil.stripHtml(ApplicationBundle.message(property), false);
  }

  public static String messageIde(String property) {
    return StringUtil.stripHtml(IdeBundle.message(property), false);
  }

  public static String messageKeyMap(String property) {
    return StringUtil.stripHtml(KeyMapBundle.message(property), false);
  }

  /*
   * Marker interface for option provider containing only descriptors which are backed by toggle actions.
   * E.g. UiSettings.SHOW_STATUS_BAR is backed by View > Status Bar action.
   */
  @SuppressWarnings({"DeprecatedIsStillUsed", "MissingDeprecatedAnnotation"})
  @Deprecated
  // for search everywhere only
  public interface CoveredByToggleActions {
  }

  private static final class CachedOptions implements Disposable {
    private static final Key<CachedOptions> KEY = Key.create("cached top hits");
    private final ConcurrentMap<Class<?>, Collection<OptionDescription>> map = ContainerUtil.newConcurrentMap();
    private final ComponentManager manager;

    private CachedOptions(ComponentManager manager) {
      this.manager = manager;
      Disposer.register(manager, this);
      manager.putUserData(KEY, this);
    }

    @Override
    public void dispose() {
      manager.putUserData(KEY, null);
      map.values().forEach(CachedOptions::dispose);
    }

    private static void dispose(Collection<? extends OptionDescription> options) {
      if (options != null) options.forEach(CachedOptions::dispose);
    }

    private static void dispose(OptionDescription option) {
      if (option instanceof Disposable) Disposer.dispose((Disposable)option);
    }
  }

  // ours ProjectLevelProvider registered in ours projectOptionsTopHitProvider extension point,
  // not in common topHitProvider, so, this adapter is required to expose ours project level providers.
  public static final class ProjectLevelProvidersAdapter implements SearchTopHitProvider {
    @Override
    public void consumeTopHits(@NotNull String pattern, @NotNull Consumer<Object> collector, @Nullable Project project) {
      if (project == null) {
        return;
      }

      pattern = checkPattern(pattern);
      if (pattern == null) {
        return;
      }

      List<String> parts = StringUtil.split(pattern, " ");
      if (parts.isEmpty()) {
        return;
      }

      for (OptionsSearchTopHitProvider.ProjectLevelProvider provider : PROJECT_LEVEL_EP.getExtensionList()) {
        doConsumeTopHits(provider, pattern, parts.get(0), collector, project);
      }
    }

    public void consumeAllTopHits(@NotNull String pattern, @NotNull Consumer<Object> collector, @Nullable Project project) {
      Matcher matcher = new WordPrefixMatcher(pattern);
      for (OptionsSearchTopHitProvider.ProjectLevelProvider provider : PROJECT_LEVEL_EP.getExtensionList()) {
        consumeTopHitsForApplicableProvider(provider, matcher, collector, project);
      }
    }
  }

  static final class Activity extends PreloadingActivity implements StartupActivity.DumbAware {
    Activity() {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw ExtensionNotApplicableException.INSTANCE;
      }
    }

    @Override
    public void preload(@NotNull ProgressIndicator indicator) {
      cacheAll(indicator, null); // for application
    }

    @Override
    public void runActivity(@NotNull Project project) {
      // for given project
      NonUrgentExecutor.getInstance().execute(() -> {
        if (project.isDisposed()) {
          return;
        }

        cacheAll(null, project);
        StartUpPerformanceService.getInstance().lastOptionTopHitProviderFinishedForProject(project);
      });
    }

    private static void cacheAll(@Nullable ProgressIndicator indicator, @Nullable Project project) {
      String name = project == null ? "application" : "project";
      com.intellij.diagnostic.Activity activity = StartUpMeasurer.startActivity("cache options in " + name);
      SearchTopHitProvider.EP_NAME.processWithPluginDescriptor((provider, pluginDescriptor) -> {
        if (provider instanceof OptionsSearchTopHitProvider && (project == null || !(provider instanceof ApplicationLevelProvider))) {
          cache((OptionsSearchTopHitProvider)provider, indicator, project, pluginDescriptor);
        }
      });

      if (project != null) {
        PROJECT_LEVEL_EP.processWithPluginDescriptor((provider, pluginDescriptor) -> {
          if (indicator != null) {
            indicator.checkCanceled();
          }
          getCachedOptions(provider, project, pluginDescriptor);
        });
      }
      activity.end();
    }

    private static void cache(@NotNull OptionsSearchTopHitProvider provider,
                              @Nullable ProgressIndicator indicator,
                              @Nullable Project project,
                              @Nullable PluginDescriptor pluginDescriptor) {
      if (indicator != null && indicator.isCanceled()) return;  // if application is closed
      if (project != null && project.isDisposed()) return; // if project is closed
      getCachedOptions(provider, project, pluginDescriptor);
    }
  }
}
