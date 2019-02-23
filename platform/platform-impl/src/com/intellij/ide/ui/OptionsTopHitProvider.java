// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.StartUpPerformanceReporter;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.StartUpMeasurer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @author Konstantin Bulenkov
 */
public abstract class OptionsTopHitProvider implements SearchTopHitProvider {
  private static final Logger LOG = Logger.getInstance(OptionsTopHitProvider.class);

  @NotNull
  public abstract Collection<OptionDescription> getOptions(@Nullable Project project);

  @NotNull
  private Collection<OptionDescription> getCachedOptions(@Nullable Project project) {
    ComponentManager manager = project != null ? project : ApplicationManager.getApplication();
    if (manager == null || manager.isDisposed()) return Collections.emptyList();

    CachedOptions cache = manager.getUserData(CachedOptions.KEY);
    if (cache == null) cache = new CachedOptions(manager);

    Class<? extends OptionsTopHitProvider> clazz = getClass();
    return cache.map.computeIfAbsent(clazz, type -> {
      StartUpMeasurer.MeasureToken measureToken = StartUpMeasurer.start(StartUpMeasurer.Activities.OPTIONS_TOP_HIT_PROVIDER, clazz.getName());
      Collection<OptionDescription> result = getOptions(project);
      measureToken.end();
      return result;
    });
  }

  @Override
  public final void consumeTopHits(@NotNull String pattern, @NotNull Consumer<Object> collector, @Nullable Project project) {
    if (!pattern.startsWith(SearchTopHitProvider.getTopHitAccelerator())) return;
    pattern = pattern.substring(1);
    final List<String> parts = StringUtil.split(pattern, " ");
    if (parts.isEmpty()) {
      return;
    }

    String id = parts.get(0);
    if (getId().startsWith(id) || pattern.startsWith(" ")) {
      pattern = pattern.startsWith(" ") ? pattern.trim() : pattern.substring(id.length()).trim().toLowerCase();
      final MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern, NameUtil.MatchingCaseSensitivity.NONE);
      for (OptionDescription option : getCachedOptions(project)) {
        if (matcher.matches(option.getOption())) {
          collector.accept(option);
        }
      }
    }
  }

  public abstract String getId();

  public boolean isEnabled(@Nullable Project project) {
    return true;
  }

  public static String messageApp(String property) {
    return StringUtil.stripHtml(ApplicationBundle.message(property), false);
  }

  static String messageIde(String property) {
    return StringUtil.stripHtml(IdeBundle.message(property), false);
  }

  static String messageKeyMap(String property) {
    return StringUtil.stripHtml(KeyMapBundle.message(property), false);
  }

  /*
   * Marker interface for option provider containing only descriptors which are backed by toggle actions.
   * E.g. UiSettings.SHOW_STATUS_BAR is backed by View > Status Bar action.
   */
  @Deprecated
  public interface CoveredByToggleActions { // for search everywhere only
  }

  private static final class CachedOptions implements Disposable {
    private static final Key<CachedOptions> KEY = Key.create("cached top hits");
    private final Map<Class<?>, Collection<OptionDescription>> map = new ConcurrentHashMap<>();
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

    private static void dispose(Collection<OptionDescription> options) {
      if (options != null) options.forEach(CachedOptions::dispose);
    }

    private static void dispose(OptionDescription option) {
      if (option instanceof Disposable) Disposer.dispose((Disposable)option);
    }
  }

  static final class Activity extends PreloadingActivity implements StartupActivity {
    @Override
    public void preload(@NotNull ProgressIndicator indicator) {
      cacheAll(indicator, null); // for application
    }

    @Override
    public void runActivity(@NotNull Project project) {
      cacheAll(null, project); // for given project
    }

    private static void cacheAll(@Nullable ProgressIndicator indicator, @Nullable Project project) {
      Application application = ApplicationManager.getApplication();
      if (application == null || application.isUnitTestMode()) {
        return;
      }

      long millis = System.currentTimeMillis();
      String name = project == null ? "application" : "project";
      Deque<ConfigurableOptionsTopHitProvider> edtProviders = new ArrayDeque<>();
      for (SearchTopHitProvider provider : SearchTopHitProvider.EP_NAME.getExtensionList()) {
        if (provider instanceof ConfigurableOptionsTopHitProvider) {
          // process on EDT, because it creates a Swing components
          // do not process all in one unified invokeLater to ensure that EDT is not blocked for a long time
          edtProviders.add((ConfigurableOptionsTopHitProvider)provider);
        }
        else if (provider instanceof OptionsTopHitProvider) {
          cache((OptionsTopHitProvider)provider, indicator, project);
        }
      }

      scheduleEdtTasks(edtProviders, indicator, project);

      long delta = System.currentTimeMillis() - millis;
      LOG.info(delta + " ms spent to cache options in " + name);
    }

    private static void scheduleEdtTasks(@NotNull Deque<ConfigurableOptionsTopHitProvider> edtProviders, @Nullable ProgressIndicator indicator, @Nullable Project project) {
      if (edtProviders.isEmpty()) {
        StartUpPerformanceReporter startUpPerformanceReporter = StartupActivity.POST_STARTUP_ACTIVITY.findExtension(StartUpPerformanceReporter.class);
        if (startUpPerformanceReporter != null) {
          startUpPerformanceReporter.lastEdtOptionTopHitProviderFinished();
        }
        return;
      }

      ApplicationManager.getApplication().invokeLater(() -> {
        ConfigurableOptionsTopHitProvider provider = edtProviders.poll();
        if (provider != null && cache(provider, indicator, project)) {
          scheduleEdtTasks(edtProviders, indicator, project);
        }
      }, ObjectUtils.chooseNotNull(project, ApplicationManager.getApplication()).getDisposed());
    }

    /**
     * returns false if disposed or cancelled
     */
    private static boolean cache(@NotNull OptionsTopHitProvider provider, @Nullable ProgressIndicator indicator, @Nullable Project project) {
      // if application is closed
      if (indicator != null && indicator.isCanceled()) {
        return false;
      }

      // if project is closed
      if (project != null && project.isDisposed()) {
        return false;
      }

      if (provider.isEnabled(project)) {
        provider.getCachedOptions(project);
      }
      return true;
    }
  }
}
