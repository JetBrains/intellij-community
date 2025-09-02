// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.updateSettings.impl.InternalPluginResults;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.PluginUpdates;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * @author Alexander Lobas
 */
public class PluginUpdatesService {
  private static final Logger LOG = Logger.getInstance(PluginUpdatesService.class);
  private static final List<PluginUpdatesService> SERVICES = new ArrayList<>();
  private static final Object ourLock = new Object();
  private static final @NotNull Condition<IdeaPluginDescriptor> DEFAULT_FILTER = // only enabled plugins by default
    descriptor -> !PluginManagerCore.isDisabled(descriptor.getPluginId());

  // FIXME it is strange that users of this class need to known which updates came from custom repositories (IJPL-6087)
  /** clients should receive filtered updates by default */
  private static @Nullable InternalPluginResults ourAllUpdates;
  private static @NotNull Condition<? super IdeaPluginDescriptor> ourFilter = DEFAULT_FILTER;
  private static boolean ourPrepared;
  private static boolean ourPreparing;
  private static boolean ourReset;

  private Consumer<? super Integer> myCountCallback;
  private List<Consumer<InternalPluginResults>> myUpdateCallbacks;
  private boolean mySetFilter;

  static {
    PluginStateManager.addStateListener(new PluginStateListener() {
      @Override
      public void install(@NotNull IdeaPluginDescriptor descriptor) {
        finishUpdate(descriptor);
      }

      @Override
      public void uninstall(@NotNull IdeaPluginDescriptor descriptor) {
        finishUpdate(descriptor);
      }
    });
  }

  @ApiStatus.Internal
  public static @NotNull PluginUpdatesService connectWithCounter(@NotNull Consumer<? super Integer> callback) {
    PluginUpdatesService service = new PluginUpdatesService();
    service.myCountCallback = callback;
    synchronized (ourLock) {
      SERVICES.add(service);
      if (ourPrepared) {
        callback.accept(getCount(getFilteredUpdateResult()));
        return service;
      }
    }
    calculateUpdates();
    return service;
  }

  @ApiStatus.Internal
  public static @NotNull PluginUpdatesService connectWithUpdates(@NotNull Consumer<@Nullable InternalPluginResults> callback) {
    PluginUpdatesService service = new PluginUpdatesService();
    service.myUpdateCallbacks = Collections.singletonList(callback);
    synchronized (ourLock) {
      SERVICES.add(service);
      if (ourPrepared) {
        callback.accept(getFilteredUpdateResult());
      }
    }
    return service;
  }

  private static @Nullable InternalPluginResults getFilteredUpdateResult() {
    synchronized (ourLock) {
      if (ourAllUpdates == null) {
        return null;
      }
      final var filter = ourFilter;
      return new InternalPluginResults(
        new PluginUpdates(
          ContainerUtil.filter(ourAllUpdates.getPluginUpdates().getAllEnabled(), d -> filter.test(d.getDescriptor())),
          ContainerUtil.filter(ourAllUpdates.getPluginUpdates().getAllDisabled(), d -> filter.test(d.getDescriptor())),
          ourAllUpdates.getPluginUpdates().getIncompatible()
        ),
        ourAllUpdates.getPluginNods(),
        ourAllUpdates.getErrors()
      );
    }
  }

  public void calculateUpdates(@NotNull Consumer<? super Collection<PluginUiModel>> callback) {
    synchronized (ourLock) {
      if (myUpdateCallbacks == null) {
        myUpdateCallbacks = new ArrayList<>();
      }
      final var adaptedCallback = adaptDescriptorConsumerToUpdateResultConsumer(callback);
      myUpdateCallbacks.add(adaptedCallback);
      if (ourPrepared) {
        adaptedCallback.accept(getFilteredUpdateResult());
        return;
      }
    }
    calculateUpdates();
  }

  private static void finishUpdate(@NotNull IdeaPluginDescriptor descriptor) {
    synchronized (ourLock) {
      if (!ourPrepared || ourAllUpdates == null) {
        return;
      }
      boolean removed = removeUpdate(descriptor.getPluginId());
      if (removed) {
        Integer countValue = getCount(getFilteredUpdateResult());
        for (PluginUpdatesService service : SERVICES) {
          service.runCountCallbacks(countValue);
        }
      }
    }
  }

  private static boolean removeUpdate(@NotNull PluginId pluginId) {
    if (ourAllUpdates == null ||
        !ContainerUtil.exists(ourAllUpdates.getPluginUpdates().getAll(), d -> Objects.equals(d.getDescriptor().getPluginId(), pluginId))) {
      return false;
    }
    ourAllUpdates = new InternalPluginResults(
      new PluginUpdates(
        ContainerUtil.filter(ourAllUpdates.getPluginUpdates().getAllEnabled(), d -> !Objects.equals(d.getDescriptor().getPluginId(), pluginId)),
        ContainerUtil.filter(ourAllUpdates.getPluginUpdates().getAllDisabled(), d -> !Objects.equals(d.getDescriptor().getPluginId(), pluginId)),
        ourAllUpdates.getPluginUpdates().getIncompatible()
      ),
      ourAllUpdates.getPluginNods(),
      ourAllUpdates.getErrors()
    );
    return true;
  }

  public void finishUpdate() {
    synchronized (ourLock) {
      if (!ourPrepared || ourAllUpdates == null) {
        return;
      }
      Integer countValue = getCount(getFilteredUpdateResult());
      for (PluginUpdatesService service : SERVICES) {
        service.runCountCallbacks(countValue);
      }
    }
  }

  public void recalculateUpdates() {
    synchronized (ourLock) {
      for (PluginUpdatesService service : SERVICES) {
        service.runAllCallbacks(null);
      }
      if (ourPreparing) {
        resetUpdates();
      }
      else {
        calculateUpdates();
      }
    }
  }

  private static void resetUpdates() {
    ourReset = true;
  }

  @ApiStatus.Internal
  public void setFilter(@NotNull Condition<? super IdeaPluginDescriptor> filter) {
    synchronized (ourLock) {
      if (!mySetFilter && ourFilter != DEFAULT_FILTER) {
        LOG.warn("Filter already set to " + ourFilter + ", new filter " + filter + " will be ignored", new Throwable());
        return;
      }
      mySetFilter = true;
      setOurFilter(filter);
    }
  }

  private static void setOurFilter(@NotNull Condition<? super IdeaPluginDescriptor> filter) {
    ourFilter = filter;
    reapplyFilter();
  }

  public static void reapplyFilter() {
    synchronized (ourLock) {
      for (PluginUpdatesService service : SERVICES) {
        service.runAllCallbacks(null);
      }
      final var filteredUpdates = getFilteredUpdateResult();
      for (PluginUpdatesService service : SERVICES) {
        service.runAllCallbacks(filteredUpdates);
      }
    }
  }

  public void dispose() {
    synchronized (ourLock) {
      dispose(this);
      if (mySetFilter) {
        setOurFilter(DEFAULT_FILTER);
        mySetFilter = false;
      }
    }
  }

  private static void dispose(@NotNull PluginUpdatesService service) {
    synchronized (ourLock) {
      SERVICES.remove(service);
      if (SERVICES.isEmpty()) {
        ourAllUpdates = null;
        ourPrepared = false;
        ourPreparing = false;
      }
    }
  }

  public static boolean isNeedUpdate(@NotNull IdeaPluginDescriptor descriptor) {
    PluginId pluginId = descriptor.getPluginId();
    synchronized (ourLock) {
      if (ourPrepared && ourAllUpdates != null) {
        final var filteredUpdates = getFilteredUpdateResult();
        assert filteredUpdates != null;
        return ContainerUtil.exists(filteredUpdates.getPluginUpdates().getAll(), d -> Objects.equals(d.getDescriptor().getPluginId(), pluginId));
      }
    }
    return InstalledPluginsState.getInstance().hasNewerVersion(pluginId);
  }


  public static @Nullable Collection<IdeaPluginDescriptor> getUpdates() {
    synchronized (ourLock) {
      if (!ourPrepared || ourPreparing) {
        return null;
      }
      final var filteredUpdates = getFilteredUpdateResult();
      return filteredUpdates == null ? null : ContainerUtil.map(filteredUpdates.getPluginUpdates().getAll(), PluginDownloader::getDescriptor);
    }
  }

  public static @Nullable @Nls String getUpdatesTooltip() {
    Collection<IdeaPluginDescriptor> updates = getUpdates();
    if (ContainerUtil.isEmpty(updates)) {
      return null;
    }
    return IdeBundle.message("updates.plugin.ready.tooltip", StringUtil.join(updates, plugin -> plugin.getName(), ", "), updates.size());
  }

  private static void calculateUpdates() {
    synchronized (ourLock) {
      if (ourPreparing) {
        return;
      }
      ourPreparing = true;
      ourAllUpdates = null;
    }
    // for example, if executed as part of Traverse UI - don't wait check updates
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return;
    }

    NonUrgentExecutor.getInstance().execute(() -> {
      final InternalPluginResults updates = UpdateChecker.getInternalPluginUpdates();
      ApplicationManager.getApplication().invokeLater(() -> {
        synchronized (ourLock) {
          ourPreparing = false;
          if (ourReset) {
            ourReset = false;
            calculateUpdates();
            return;
          }
          ourPrepared = true;
          ourAllUpdates = updates;
          final var filteredUpdates = getFilteredUpdateResult();
          for (PluginUpdatesService service : SERVICES) {
            service.runAllCallbacks(filteredUpdates);
          }
        }
      }, ModalityState.any());
    });
  }

  private void runAllCallbacks(@Nullable InternalPluginResults filteredUpdates) {
    runCountCallbacks(getCount(filteredUpdates));
    if (myUpdateCallbacks != null) {
      for (var callback : myUpdateCallbacks) {
        callback.accept(filteredUpdates);
      }
    }
  }

  private void runCountCallbacks(@Nullable Integer countValue) {
    if (myCountCallback != null) {
      myCountCallback.accept(countValue);
    }
  }

  private static @Nullable Integer getCount(@Nullable InternalPluginResults filteredUpdates) {
    return filteredUpdates == null ? null : filteredUpdates.getPluginUpdates().getAll().size();
  }

  private static @NotNull Consumer<InternalPluginResults> adaptDescriptorConsumerToUpdateResultConsumer(
    @NotNull Consumer<? super Collection<PluginUiModel>> consumer
  ) {
    return updateResult -> {
      if (updateResult == null) consumer.accept(null);
      else consumer.accept(ContainerUtil.map(updateResult.getPluginUpdates().getAll(), downloader -> new PluginUiModelAdapter(downloader.getDescriptor())));
    };
  }
}
