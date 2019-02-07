// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.application.ApplicationManager
import java.io.File

/**
 * An entrypoint class to record in event log an information about feature usages.
 *
 * There are two types of events:
 * 1) Regular events, recorded when they occur, e.g. open project, invoked action;
 * 2) State events, should be recorded regularly by scheduler, e.g. configured libraries/frameworks;
 *
 * Each event might be recorded together with an additional (context) information, e.g. source and shortcut for action.
 *
 * Note: FeatureUsageCollector API use this class under the hood.
 * Therefore, if you record statistic with FeatureUsageCollector API there's no need to record events in event log manually.
 *
 * @see com.intellij.internal.statistic.service.fus.collectors.FeatureUsagesCollector
 * @see com.intellij.internal.statistic.service.fus.collectors.ApplicationUsageTriggerCollector
 * @see com.intellij.internal.statistic.service.fus.collectors.ProjectUsageTriggerCollector
 */
object FeatureUsageLogger {
  private val ourLogger : FeatureUsageEventLogger

  init {
    val provider = getLoggerProvider()
    ourLogger = if (provider.isEnabled()) provider.createLogger() else FeatureUsageEmptyEventLogger()

    if (isEnabled()) {
      ApplicationManager.getApplication().executeOnPooledThread { initStateEventTrackers(); }
    }
  }

  /**
   * Records that in a group (e.g. 'dialogs', 'intentions') a new event occurred.
   */
  fun log(group: FeatureUsageGroup, action: String) {
    return ourLogger.log(group, action, false)
  }

  /**
   * Records that in a group (e.g. 'dialogs', 'intentions') a new event occurred.
   * Adds context information to the event, e.g. source and shortcut for an action.
   */
  fun log(group: FeatureUsageGroup, action: String, data: Map<String, Any>) {
    return ourLogger.log(group, action, data, false)
  }

  /**
   * Records a new state event in a group (e.g. 'run.configuration.type').
   */
  fun logState(group: FeatureUsageGroup, action: String) {
    return ourLogger.log(group, action, true)
  }

  /**
   * Records a new state event in a group (e.g. 'run.configuration.type').
   * Adds context information to the event, e.g. if configuration is stored on project or on IDE level.
   */
  fun logState(group: FeatureUsageGroup, action: String, data: Map<String, Any>) {
    return ourLogger.log(group, action, data, true)
  }

  /**
   * use [log] with FeatureUsageGroup instead
   * @deprecated
   */
  fun log(groupId: String, action: String) {
    return ourLogger.log(FeatureUsageGroup(groupId, 1), action, true)
  }

  fun getLogFiles() : List<File> {
    return ourLogger.getLogFiles()
  }

  fun isEnabled() : Boolean {
    return ourLogger !is FeatureUsageEmptyEventLogger
  }
}
