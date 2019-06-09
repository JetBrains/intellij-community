// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.diagnostic.ParallelActivity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.startUpPerformanceReporter.StartUpPerformanceReporter;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * @author Konstantin Bulenkov
 */
public abstract class OptionsTopHitProvider implements OptionsSearchTopHitProvider, SearchTopHitProvider {
  private static final Logger LOG = Logger.getInstance(OptionsTopHitProvider.class);

  // project level here means not that EP itself in project area, but that extensions applicable for project only
  private static final ExtensionPointName<OptionsSearchTopHitProvider.ProjectLevelProvider>
    PROJECT_LEVEL_EP = new ExtensionPointName<>("com.intellij.search.projectOptionsTopHitProvider");

  /**
   * @deprecated Use {@link OptionsSearchTopHitProvider.ApplicationLevelProvider} or {@link OptionsSearchTopHitProvider.ProjectLevelProvider}
   *
   * ConfigurableOptionsTopHitProvider will be refactored later.
   */
  @NotNull
  @Deprecated
  public abstract Collection<OptionDescription> getOptions(@Nullable Project project);

  @NotNull
  private static Collection<OptionDescription> getCachedOptions(@NotNull OptionsSearchTopHitProvider provider,
                                                                @Nullable Project project,
                                                                @Nullable PluginDescriptor pluginDescriptor) {
    ComponentManager manager = project == null || provider instanceof ApplicationLevelProvider ? ApplicationManager.getApplication() : project;
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
    (project == null ? ParallelActivity.APP_OPTIONS_TOP_HIT_PROVIDER : ParallelActivity.PROJECT_OPTIONS_TOP_HIT_PROVIDER)
      .record(startTime, clazz, pluginDescriptor == null ? null : pluginDescriptor.getPluginId().getIdString());

    Collection<OptionDescription> prevValue = cache.map.putIfAbsent(clazz, result);
    return prevValue == null ? result : prevValue;
  }

  @Override
  public final void consumeTopHits(@NotNull String pattern, @NotNull Consumer<Object> collector, @Nullable Project project) {
    consumeTopHits(this, pattern, collector, project);
  }

  static void consumeTopHits(@NotNull OptionsSearchTopHitProvider provider, @NotNull String pattern, @NotNull Consumer<Object> collector, @Nullable Project project) {
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
      pattern = pattern.startsWith(" ") ? pattern.trim() : StringUtil.toLowerCase(pattern.substring(id.length()).trim());
      MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern, NameUtil.MatchingCaseSensitivity.NONE);
      consumeTopHitsForApplicableProvider(provider, matcher, collector, project);
    }
  }

  private static void consumeTopHitsForApplicableProvider(@NotNull OptionsSearchTopHitProvider provider,
                                                          @NotNull MinusculeMatcher matcher,
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
  @SuppressWarnings("DeprecatedIsStillUsed")
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
      MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern, NameUtil.MatchingCaseSensitivity.NONE);
      for (OptionsSearchTopHitProvider.ProjectLevelProvider provider : PROJECT_LEVEL_EP.getExtensionList()) {
        consumeTopHitsForApplicableProvider(provider, matcher, collector, project);
      }
    }
  }

  static final class Activity extends PreloadingActivity implements StartupActivity, DumbAware {
    @Override
    public void preload(@NotNull ProgressIndicator indicator) {
      cacheAll(indicator, null); // for application
    }

    @Override
    public void runActivity(@NotNull Project project) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> cacheAll(null, project)); // for given project
    }

    private static void cacheAll(@Nullable ProgressIndicator indicator, @Nullable Project project) {
      Application app = ApplicationManager.getApplication();
      if (app == null || app.isUnitTestMode()) {
        return;
      }

      long millis = System.currentTimeMillis();
      String name = project == null ? "application" : "project";
      Deque<Pair<ConfigurableOptionsTopHitProvider, PluginDescriptor>> edtProviders = new ArrayDeque<>();
      SearchTopHitProvider.EP_NAME.processWithPluginDescriptor((provider, pluginDescriptor) -> {
        if (provider instanceof ConfigurableOptionsTopHitProvider) {
          // process on EDT, because it creates a Swing components
          // do not process all in one unified invokeLater to ensure that EDT is not blocked for a long time
          edtProviders.add(new Pair<>((ConfigurableOptionsTopHitProvider)provider, pluginDescriptor));
        }
        else if (provider instanceof OptionsSearchTopHitProvider) {
          if (project != null && provider instanceof ApplicationLevelProvider) {
            return;
          }

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

      scheduleEdtTasks(edtProviders, indicator, project);

      long delta = System.currentTimeMillis() - millis;
      LOG.info(delta + " ms spent to cache options in " + name);
    }

    private static void scheduleEdtTasks(@NotNull Deque<Pair<ConfigurableOptionsTopHitProvider, PluginDescriptor>> edtProviders, @Nullable ProgressIndicator indicator, @Nullable Project project) {
      if (edtProviders.isEmpty()) {
        if (project != null) {
          StartUpPerformanceReporter startUpPerformanceReporter = StartupActivity.POST_STARTUP_ACTIVITY.findExtension(StartUpPerformanceReporter.class);
          if (startUpPerformanceReporter != null) {
            startUpPerformanceReporter.lastEdtOptionTopHitProviderFinishedForProject();
          }
        }
        return;
      }

      ApplicationManager.getApplication().invokeLater(() -> {
        Pair<ConfigurableOptionsTopHitProvider, PluginDescriptor> providerAndPluginDescriptor = edtProviders.pollFirst();
        if (providerAndPluginDescriptor != null && cache(providerAndPluginDescriptor.first, indicator, project, providerAndPluginDescriptor.second)) {
          scheduleEdtTasks(edtProviders, indicator, project);
        }
      }, ObjectUtils.chooseNotNull(project, ApplicationManager.getApplication()).getDisposed());
    }

    /**
     * returns false if disposed or cancelled
     */
    private static boolean cache(@NotNull OptionsSearchTopHitProvider provider, @Nullable ProgressIndicator indicator, @Nullable Project project, @Nullable PluginDescriptor pluginDescriptor) {
      // if application is closed
      if (indicator != null && indicator.isCanceled()) {
        return false;
      }

      // if project is closed
      if (project != null && project.isDisposed()) {
        return false;
      }

      getCachedOptions(provider, project, pluginDescriptor);
      return true;
    }
  }
}
