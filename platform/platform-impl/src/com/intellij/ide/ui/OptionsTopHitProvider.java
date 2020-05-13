// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.StartUpPerformanceService;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.WordPrefixMatcher;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public abstract class OptionsTopHitProvider implements OptionsSearchTopHitProvider, SearchTopHitProvider {
  // project level here means not that EP itself in project area, but that extensions applicable for project only
  public static final ExtensionPointName<OptionsSearchTopHitProvider.ProjectLevelProvider>
    PROJECT_LEVEL_EP = new ExtensionPointName<>("com.intellij.search.projectOptionsTopHitProvider");

  /**
   * @deprecated Use {@link OptionsSearchTopHitProvider.ApplicationLevelProvider} or {@link OptionsSearchTopHitProvider.ProjectLevelProvider}
   * <p>
   * ConfigurableOptionsTopHitProvider will be refactored later.
   */
  @Deprecated
  public abstract @NotNull Collection<OptionDescription> getOptions(@Nullable Project project);

  private static @NotNull Collection<OptionDescription> getCachedOptions(@NotNull OptionsSearchTopHitProvider provider,
                                                                         @Nullable Project project,
                                                                         @Nullable PluginDescriptor pluginDescriptor) {
    TopHitCache cache = project == null || provider instanceof ApplicationLevelProvider
       ? TopHitCache.getInstance()
       : ProjectTopHitCache.getInstance(project);

    return cache.getCachedOptions(provider, project, pluginDescriptor);
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

  private static @Nullable String checkPattern(@NotNull String pattern) {
    if (!pattern.startsWith(SearchTopHitProvider.getTopHitAccelerator())) {
      return null;
    }

    pattern = pattern.substring(1);
    return pattern;
  }

  @Override
  public abstract @NotNull String getId();

  public static String messageApp(@PropertyKey(resourceBundle = ApplicationBundle.BUNDLE) String property) {
    return StringUtil.stripHtml(ApplicationBundle.message(property), false);
  }

  public static String messageIde(@PropertyKey(resourceBundle = IdeBundle.BUNDLE) String property) {
    return StringUtil.stripHtml(IdeBundle.message(property), false);
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
          OptionsSearchTopHitProvider p = (OptionsSearchTopHitProvider)provider;
          if (p.preloadNeeded() && (indicator == null || !indicator.isCanceled()) && (project == null || !project.isDisposed())) {
            getCachedOptions(p, project, pluginDescriptor);
          }
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
  }
}
