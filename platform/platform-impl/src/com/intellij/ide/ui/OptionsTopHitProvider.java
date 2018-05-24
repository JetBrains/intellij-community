// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationBundle;
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
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static java.util.Collections.emptyList;

/**
 * @author Konstantin Bulenkov
 */
public abstract class OptionsTopHitProvider implements SearchTopHitProvider {
  private static final Logger LOG = Logger.getInstance(OptionsTopHitProvider.class);

  @NotNull
  public abstract Collection<OptionDescription> getOptions(@Nullable Project project);

  private Collection<OptionDescription> getCachedOptions(@Nullable Project project) {
    ComponentManager manager = project != null ? project : getApplication();
    if (manager == null || manager.isDisposed()) return emptyList();

    CachedOptions cache = manager.getUserData(CachedOptions.KEY);
    if (cache == null) cache = new CachedOptions(manager);

    return cache.map.computeIfAbsent(getClass(), type -> getOptions(project));
  }

  @Override
  public final void consumeTopHits(@NonNls String pattern, Consumer<Object> collector, Project project) {
    if (!pattern.startsWith("#")) return;
    pattern = pattern.substring(1);
    final List<String> parts = StringUtil.split(pattern, " ");

    if (parts.size() == 0) {
      return;
    }

    String id = parts.get(0);
    if (getId().startsWith(id) || pattern.startsWith(" ")) {
      if (pattern.startsWith(" ")) {
        pattern = pattern.trim();
      } else {
        pattern = pattern.substring(id.length()).trim().toLowerCase();
      }
      final MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern, NameUtil.MatchingCaseSensitivity.NONE);
      for (OptionDescription option : getCachedOptions(project)) {
        if (matcher.matches(option.getOption())) {
          collector.consume(option);
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
    private final ConcurrentHashMap<Class<?>, Collection<OptionDescription>> map = new ConcurrentHashMap<>();
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

  public static final class Activity extends PreloadingActivity implements StartupActivity {
    @Override
    public void preload(@NotNull ProgressIndicator indicator) {
      cacheAll(indicator, null); // for application
    }

    @Override
    public void runActivity(@NotNull Project project) {
      cacheAll(null, project); // for given project
    }

    private static void cacheAll(@Nullable ProgressIndicator indicator, @Nullable Project project) {
      Application application = getApplication();
      if (application != null && !application.isUnitTestMode()) {
        long millis = System.currentTimeMillis();
        String name = project == null ? "application" : "project";
        AtomicLong time = new AtomicLong();
        for (SearchTopHitProvider provider : SearchTopHitProvider.EP_NAME.getExtensions()) {
          if (provider instanceof ConfigurableOptionsTopHitProvider) {
            // process on EDT, because it creates a Swing components
            application.invokeLater(() -> {
              long millisOnEDT = System.currentTimeMillis();
              cache((ConfigurableOptionsTopHitProvider)provider, indicator, project);
              time.addAndGet(System.currentTimeMillis() - millisOnEDT);
            });
          }
          else if (provider instanceof OptionsTopHitProvider) {
            cache((OptionsTopHitProvider)provider, indicator, project);
          }
        }
        application.invokeLater(() -> LOG.info(time.get() + " ms spent on EDT to cache options in " + name));
        long delta = System.currentTimeMillis() - millis;
        LOG.info(delta + " ms spent to cache options in " + name);
      }
    }

    private static void cache(@NotNull OptionsTopHitProvider provider, @Nullable ProgressIndicator indicator, @Nullable Project project) {
      if (indicator != null && indicator.isCanceled()) return; // if application is closed
      if (project != null && project.isDisposed()) return; // if project is closed
      if (provider.isEnabled(project)) provider.getCachedOptions(project);
    }
  }
}
