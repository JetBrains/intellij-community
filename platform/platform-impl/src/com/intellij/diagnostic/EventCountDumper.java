// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.ide.IdeEventQueue;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class EventCountDumper {
  public static final String EVENT_COUNTS_HEADER = "---------- Event counts ----------";

  public static ThreadDump addEventCountersTo(ThreadDump threadDump) {
    IdeEventQueue eventQueue = IdeEventQueue.getInstance();
    String enrichedDump = threadDump.getRawDump() +
                          "\n" + EVENT_COUNTS_HEADER +
                          "\nPosted: " + eventQueue.getPostedEventCount() +
                          "\nPosted (system): " + eventQueue.getPostedSystemEventCount() +
                          "\nReturned: " + eventQueue.getReturnedEventCount();
    return new ThreadDump(enrichedDump, threadDump.getEDTStackTrace(), threadDump.getThreadInfos());
  }
}
