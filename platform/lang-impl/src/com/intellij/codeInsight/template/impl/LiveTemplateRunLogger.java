// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import kotlin.Triple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class LiveTemplateRunLogger extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("live.templates", 48);
  private static final StringEventField TEMPLATE_GROUP = EventFields.StringValidatedByCustomRule("group", LiveTemplateValidator.class);
  private static final StringEventField KEY = EventFields.StringValidatedByCustomRule("key", LiveTemplateValidator.class);
  private static final BooleanEventField CHANGED_BY_USER = EventFields.Boolean("changedByUser");
  private static final VarargEventId STARTED = registerLiveTemplateEvent(GROUP, "started");


  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static VarargEventId registerLiveTemplateEvent(EventLogGroup group, String event) {
    return group.registerVarargEvent(event, EventFields.Language,
                                     TEMPLATE_GROUP,
                                     EventFields.PluginInfo,
                                     KEY,
                                     CHANGED_BY_USER);
  }

  static void log(@NotNull Project project, @NotNull TemplateImpl template, @NotNull Language language) {
    final List<EventPair<?>> data = createTemplateData(template, language);
    if (data != null) {
      STARTED.log(project, data);
    }
  }

  static @Nullable Triple<String, String, PluginInfo> getKeyGroupPluginToLog(@NotNull TemplateImpl template) {
    String key = template.getKey();
    String groupName = template.getGroupName();
    if (isCreatedProgrammatically(key, groupName)) return null;

    PluginInfo plugin = TemplateSettings.getInstance().findPluginForPredefinedTemplate(template);
    if (plugin == null) {
      key = "user.defined.template";
      groupName = "user.defined.group";
    }
    else if (!plugin.isSafeToReport()) {
      key = "custom.plugin.template";
      groupName = "custom.plugin.group";
    }
    return new Triple<>(key, groupName, plugin);
  }

  static @Nullable List<EventPair<?>> createTemplateData(@NotNull TemplateImpl template, @NotNull Language language) {
    Triple<String, String, PluginInfo> keyGroupPluginToLog = getKeyGroupPluginToLog(template);
    if (keyGroupPluginToLog == null) {
      return null;
    }

    List<EventPair<?>> data = new ArrayList<>();
    data.add(EventFields.Language.with(language));
    data.add(TEMPLATE_GROUP.with(keyGroupPluginToLog.getSecond()));
    PluginInfo plugin = keyGroupPluginToLog.getThird();
    if (plugin != null) {
      data.add(EventFields.PluginInfo.with(plugin));
    }
    data.add(KEY.with(keyGroupPluginToLog.getFirst()));
    data.add(CHANGED_BY_USER.with(TemplateSettings.getInstance().differsFromDefault(template)));
    return data;
  }

  private static boolean isCreatedProgrammatically(String key, String groupName) {
    return StringUtil.isEmpty(key) || StringUtil.isEmpty(groupName);
  }

  public static class LiveTemplateValidator extends CustomValidationRule {
    @Override
    public @NotNull String getRuleId() {
      return "live_template";
    }

    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return getRuleId().equals(ruleId) || "live_template_group".equals(ruleId) ;
    }

    @Override
    protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      final String key = getEventDataField(context, "key");
      final String group = getEventDataField(context, "group");
      if (key == null || group == null || !isKeyOrGroup(data, key, group)) {
        return ValidationResultType.REJECTED;
      }
      return validateKeyGroup(key, group);
    }

    public static @NotNull ValidationResultType validateKeyGroup(String key, Object group) {
      if (group == null) return ValidationResultType.REJECTED;
      if ("user.defined.template".equals(key) && "user.defined.group".equals(group)) return ValidationResultType.ACCEPTED;
      if ("custom.plugin.template".equals(key) && "custom.plugin.group".equals(group)) return ValidationResultType.ACCEPTED;
      try {
        TemplateImpl template = TemplateSettings.getInstance().getTemplate(key, group.toString());
        if (template != null) {
          PluginInfo info = TemplateSettings.getInstance().findPluginForPredefinedTemplate(template);
          if (info != null && info.isSafeToReport()) return ValidationResultType.ACCEPTED;
        }
      } catch (Exception ignored) { }
      return ValidationResultType.REJECTED;
    }

    private static boolean isKeyOrGroup(@NotNull String data, @NotNull String key, @NotNull String group) {
      return StringUtil.equals(data, key) || StringUtil.equals(data, group);
    }
  }
}
