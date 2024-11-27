// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.BooleanEventField;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.impl.KeymapFlagsStorage;
import com.intellij.openapi.keymap.impl.ui.ActionsTree;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class KeymapChangesCollector extends ApplicationUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("keymap.changes", 4);

  private static final BooleanEventField IMPORTED = EventFields.Boolean("imported");

  private static final VarargEventId KEYMAP_CHANGE = GROUP.registerVarargEvent("keymap.change", ActionsEventLogGroup.ACTION_ID, IMPORTED);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  public @NotNull Set<MetricEvent> getMetrics() {
    KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager == null) {
      return Collections.emptySet();
    }

    Keymap keymap = keymapManager.getActiveKeymap();
    if (!keymap.canModify()) {
      // default keymap can't be customized
      return Collections.emptySet();
    }

    Set<MetricEvent> data = new HashSet<>();
    KeymapFlagsStorage keymapFlagsStorage = ApplicationManager.getApplication().getService(KeymapFlagsStorage.class);

    for (String action_id : keymap.getActionIds()) {
      if (ActionsTree.isShortcutCustomized(action_id, keymap)) {
        if (keymapFlagsStorage.hasFlag(keymap, action_id, KeymapFlagsStorage.FLAG_MIGRATED_SHORTCUT)) {
          data.add(KEYMAP_CHANGE.metric(ActionsEventLogGroup.ACTION_ID.with(action_id), IMPORTED.with(true)));
        }
        else {
          data.add(KEYMAP_CHANGE.metric(ActionsEventLogGroup.ACTION_ID.with(action_id))); //reducing data by not reporting optional flag
        }
      }
    }

    return data;
  }
}
