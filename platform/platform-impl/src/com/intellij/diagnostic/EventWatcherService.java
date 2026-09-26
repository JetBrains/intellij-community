// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.diagnostic.fus.FusReportingEventWatcher;

import java.util.ArrayList;

/**
 * Adapter: setups EventWatchers according to system properties configuration, and wraps them all into a single EventWatcher service.
 */
final class EventWatcherService extends CompositeEventWatcher {
  EventWatcherService() {
    super(createWatchersAccordingToConfiguration());
  }

  private static EventWatcher[] createWatchersAccordingToConfiguration() {
    final ArrayList<EventWatcher> watchers = new ArrayList<>();
    if (isEnabledDetailed) {
      watchers.add(new DetailedEventWatcher());
    }
    if (isEnabledAggregated) {
      watchers.add(new OtelReportingEventWatcher());
    }
    if (isEnabledFus) {
      watchers.add(new FusReportingEventWatcher());
    }
    return watchers.toArray(EventWatcher[]::new);
  }
}
