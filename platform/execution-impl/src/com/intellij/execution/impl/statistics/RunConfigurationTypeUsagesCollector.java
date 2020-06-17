// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl.statistics;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.*;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.*;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomWhiteListRule;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.components.StoredProperty;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.*;

public class RunConfigurationTypeUsagesCollector extends ProjectUsagesCollector {
  public static final String CONFIGURED_IN_PROJECT = "configured.in.project";
  public static final EventLogGroup GROUP = new EventLogGroup("run.configuration.type", 7);
  public static final StringEventField ID_FIELD = EventFields.String("id").withCustomRule("run_config_id");
  public static final StringEventField FACTORY_FIELD = EventFields.String("factory").withCustomRule("run_config_factory");
  private static final IntEventField COUNT_FIELD = EventFields.Int("count");
  private static final StringEventField FEATURE_NAME_FIELD = EventFields.String("featureName").withCustomRule("plugin_info");
  private static final BooleanEventField SHARED_FIELD = EventFields.Boolean("shared");
  private static final BooleanEventField EDIT_BEFORE_RUN_FIELD = EventFields.Boolean("edit_before_run");
  private static final BooleanEventField ACTIVATE_BEFORE_RUN_FIELD = EventFields.Boolean("activate_before_run");
  private static final BooleanEventField TEMPORARY_FIELD = EventFields.Boolean("temporary");
  private static final BooleanEventField PARALLEL_FIELD = EventFields.Boolean("parallel");
  private static final ObjectEventField ADDITIONAL_FIELD = EventFields.createAdditionalDataField(GROUP.getId(), CONFIGURED_IN_PROJECT);
  private static final VarargEventId CONFIGURED_IN_PROJECT_EVENT =
    GROUP.registerVarargEvent(CONFIGURED_IN_PROJECT, COUNT_FIELD, ID_FIELD, FACTORY_FIELD, SHARED_FIELD, EDIT_BEFORE_RUN_FIELD,
                              ACTIVATE_BEFORE_RUN_FIELD, TEMPORARY_FIELD, PARALLEL_FIELD, ADDITIONAL_FIELD);
  private static final VarargEventId FEATURE_USED_EVENT =
    GROUP.registerVarargEvent("feature.used", COUNT_FIELD, ID_FIELD, EventFields.PluginInfo, FEATURE_NAME_FIELD);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @NotNull
  @Override
  public CancellablePromise<Set<MetricEvent>> getMetrics(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    AsyncPromise<Set<MetricEvent>> result = new AsyncPromise<>();
    UIUtil.invokeLaterIfNeeded(() -> {
      try {
        TObjectIntHashMap<Template> templates = new TObjectIntHashMap<>();
        if (project.isDisposed()) {
          result.setResult(Collections.emptySet());
          return;
        }
        RunManager runManager = RunManager.getInstance(project);
        for (RunnerAndConfigurationSettings settings : runManager.getAllSettings()) {
          ProgressManager.checkCanceled();
          RunConfiguration runConfiguration = settings.getConfiguration();
          final ConfigurationFactory configurationFactory = runConfiguration.getFactory();
          if (configurationFactory == null) {
            // not realistic
            continue;
          }

          final ConfigurationType configurationType = configurationFactory.getType();
          List<EventPair> pairs = createFeatureUsageData(configurationType, configurationFactory);
          pairs.addAll(getSettings(settings, runConfiguration));
          final Template template = new Template(CONFIGURED_IN_PROJECT_EVENT, pairs);
          addOrIncrement(templates, template);
          collectRunConfigurationFeatures(runConfiguration, templates);
          if (runConfiguration instanceof FusAwareRunConfiguration) {
            List<EventPair> additionalData = ((FusAwareRunConfiguration)runConfiguration).getAdditionalUsageData();
            pairs.add(ADDITIONAL_FIELD.with(new ObjectEventData(additionalData.toArray(new EventPair[0]))));
          }
        }
        Set<MetricEvent> metrics = new HashSet<>();
        templates.forEachEntry((template, value) -> {
          metrics.add(template.createMetricEvent(value));
          return true;
        });
        result.setResult(metrics);
      }
      catch (Throwable t) {
        result.setError(t);
        throw t;
      }
    });
    return result;
  }

  private static void addOrIncrement(TObjectIntHashMap<Template> templates, Template template) {
    if (templates.containsKey(template)) {
      templates.increment(template);
    }
    else {
      templates.put(template, 1);
    }
  }

  private static void collectRunConfigurationFeatures(RunConfiguration runConfiguration, TObjectIntHashMap<Template> templates) {
    if (runConfiguration instanceof RunConfigurationBase) {
      PluginInfo info = PluginInfoDetectorKt.getPluginInfo(runConfiguration.getClass());
      if (!info.isSafeToReport()) return;
      Object state = ((RunConfigurationBase)runConfiguration).getState();
      if (state instanceof RunConfigurationOptions) {
        RunConfigurationOptions runConfigurationOptions = (RunConfigurationOptions)state;
        List<StoredProperty<Object>> properties = runConfigurationOptions.__getProperties();
        for (StoredProperty<Object> property : properties) {
          String name = property.getName();
          if (name == null || name.equals("isAllowRunningInParallel") || name.equals("isNameGenerated")) continue;
          Object value = property.getValue(runConfigurationOptions);
          boolean featureUsed;
          if (value instanceof Boolean) {
            featureUsed = (Boolean)value;
          }
          else if (value instanceof String) {
            featureUsed = StringUtil.isNotEmpty((String)value);
          }
          else if (value instanceof Collection) {
            featureUsed = ((Collection)value).size() > 0;
          }
          else if (value instanceof Map) {
            featureUsed = ((Map)value).size() > 0;
          }
          else {
            continue;
          }
          if (featureUsed) {
            List<EventPair> pairs = new ArrayList<>();
            pairs.add(ID_FIELD.with(runConfiguration.getType().getId()));
            pairs.add(EventFields.PluginInfo.with(info));
            pairs.add(FEATURE_NAME_FIELD.with(name));
            addOrIncrement(templates, new Template(FEATURE_USED_EVENT, pairs));
          }
        }
      }
    }
  }

  public static @NotNull List<EventPair> createFeatureUsageData(@NotNull ConfigurationType configuration, @Nullable ConfigurationFactory factory) {
    final String id = configuration instanceof UnknownConfigurationType ? "unknown" : configuration.getId();
    List<EventPair> pairs = new ArrayList<>();
    pairs.add(ID_FIELD.with(id));
    if (factory != null && configuration.getConfigurationFactories().length > 1) {
      pairs.add(FACTORY_FIELD.with(factory.getId()));
    }
    return pairs;
  }

  private static @NotNull ArrayList<EventPair<Boolean>> getSettings(@NotNull RunnerAndConfigurationSettings settings,
                                                                    @NotNull RunConfiguration runConfiguration) {
    return ContainerUtil.newArrayList(SHARED_FIELD.with(settings.isShared()),
                                      EDIT_BEFORE_RUN_FIELD.with(settings.isEditBeforeRun()),
                                      ACTIVATE_BEFORE_RUN_FIELD.with(settings.isActivateToolWindowBeforeRun()),
                                      PARALLEL_FIELD.with(runConfiguration.isAllowRunningInParallel()),
                                      TEMPORARY_FIELD.with(settings.isTemporary()));
  }

  private static final class Template {
    private final VarargEventId myEventId;
    private final List<EventPair> myEventPairs;

    private Template(VarargEventId id,
                     List<EventPair> pairs) {
      myEventId = id;
      myEventPairs = pairs;
    }

    @NotNull
    private MetricEvent createMetricEvent(int count) {
      myEventPairs.add(COUNT_FIELD.with(count));
      return myEventId.metric(myEventPairs.toArray(new EventPair[0]));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Template template = (Template)o;
      return Objects.equals(myEventId, template.myEventId) &&
             Objects.equals(myEventPairs, template.myEventPairs);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myEventId, myEventPairs);
    }
  }

  public static class RunConfigurationUtilValidator extends CustomWhiteListRule {
    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return "run_config_id".equals(ruleId) || "run_config_factory".equals(ruleId);
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if (isThirdPartyValue(data) || "unknown".equals(data)) return ValidationResultType.ACCEPTED;

      final String configurationId = getEventDataField(context, ID_FIELD.getName());
      final String factoryId = getEventDataField(context, FACTORY_FIELD.getName());
      if (configurationId == null) {
        return ValidationResultType.REJECTED;
      }

      if (StringUtil.equals(data, configurationId) || StringUtil.equals(data, factoryId)) {
        final Pair<ConfigurationType, ConfigurationFactory> configurationAndFactory =
          findConfigurationAndFactory(configurationId, factoryId);

        final ConfigurationType configuration = configurationAndFactory.getFirst();
        final ConfigurationFactory factory = configurationAndFactory.getSecond();
        if (configuration != null && (StringUtil.isEmpty(factoryId) || factory != null)) {
          final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(configuration.getClass());
          context.setPluginInfo(info);
          return info.isDevelopedByJetBrains() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
        }
      }
      return ValidationResultType.REJECTED;
    }

    @NotNull
    private static Pair<ConfigurationType, ConfigurationFactory> findConfigurationAndFactory(@NotNull String configurationId,
                                                                                             @Nullable String factoryId) {
      final ConfigurationType configuration = findRunConfigurationById(configurationId);
      if (configuration == null) {
        return Pair.empty();
      }

      final ConfigurationFactory factory = StringUtil.isEmpty(factoryId) ? null : findFactoryById(configuration, factoryId);
      return Pair.create(configuration, factory);
    }

    @Nullable
    private static ConfigurationType findRunConfigurationById(@NotNull String configuration) {
      final ConfigurationType[] types = ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions();
      for (ConfigurationType type : types) {
        if (StringUtil.equals(type.getId(), configuration)) {
          return type;
        }
      }
      return null;
    }

    @Nullable
    private static ConfigurationFactory findFactoryById(@NotNull ConfigurationType configuration, @NotNull String factoryId) {
      for (ConfigurationFactory factory : configuration.getConfigurationFactories()) {
        if (StringUtil.equals(factory.getId(), factoryId)) {
          return factory;
        }
      }
      return null;
    }
  }
}
