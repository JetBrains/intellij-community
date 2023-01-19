// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import java.util.ArrayList;

/**
 * Adapter: setups EventWatchers according to system properties configuration, and wraps them all
 * into single EventWatcher service.
 */
final class EventWatcherService extends CompositeEventWatcher {
  EventWatcherService() {
    super(createWatchersAccordingToConfiguration());
  }


  private static EventWatcher[] createWatchersAccordingToConfiguration(){
    final ArrayList<EventWatcher> watchers = new ArrayList<>();
    if (EventWatcher.isEnabledDetailed){
      watchers.add(new DetailedEventWatcher());
    }
    if(EventWatcher.isEnabledAggregated){
      watchers.add(new OtelReportingEventWatcher());
    }
    return watchers.toArray(EventWatcher[]::new);
  }
}
