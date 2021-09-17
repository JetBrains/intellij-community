//// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//package org.jetbrains.jps.cache.statistics;
//
//import com.intellij.internal.statistic.eventLog.EventLogGroup;
//import com.intellij.internal.statistic.eventLog.events.EventFields;
//import com.intellij.internal.statistic.eventLog.events.EventId;
//import com.intellij.internal.statistic.eventLog.events.EventId1;
//import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
//
//public class JpsCacheUsagesCollector extends CounterUsagesCollector {
//  private static final EventLogGroup GROUP = new EventLogGroup("jps.cache", 3);
//  public static final EventId DOWNLOAD_THROUGH_NOTIFICATION_EVENT_ID = GROUP.registerEvent("download.through.notification");
//  public static final EventId1<Long> DOWNLOAD_CACHE_SIZE_EVENT_ID = GROUP.registerEvent("caches.downloaded", EventFields.Long("download_cache_size"));
//  public static final EventId1<Long> DOWNLOAD_BINARY_SIZE_EVENT_ID = GROUP.registerEvent("caches.downloaded", EventFields.Long("download_binary_size"));
//  public static final EventId1<Long> DOWNLOAD_DURATION_EVENT_ID = GROUP.registerEvent("caches.downloaded", EventFields.Long("duration"));
//
//  @Override
//  public EventLogGroup getGroup() {
//    return GROUP;
//  }
//}
