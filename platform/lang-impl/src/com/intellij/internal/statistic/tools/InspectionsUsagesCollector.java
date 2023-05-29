// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.tools;

import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionUsageStorage;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.ide.scratch.ScratchesNamedScope;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.collectors.fus.PluginInfoValidationRule;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.impl.OpenFilesScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.search.scope.*;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Attribute;
import org.jdom.Content;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public final class InspectionsUsagesCollector extends ProjectUsagesCollector {
  private static final Predicate<ScopeToolState> ENABLED = state -> !state.getTool().isEnabledByDefault() && state.isEnabled();
  private static final Predicate<ScopeToolState> DISABLED = state -> state.getTool().isEnabledByDefault() && !state.isEnabled();

  private static final List<String> ALLOWED_SCOPES = List.of(
    "custom",
    CustomScopesProviderEx.getAllScope().getScopeId(),
    ProjectFilesScope.INSTANCE.getScopeId(),
    NonProjectFilesScope.NAME,
    ProjectProductionScope.INSTANCE.getScopeId(),
    TestsScope.NAME,
    OpenFilesScope.INSTANCE.getScopeId(),
    GeneratedFilesScope.INSTANCE.getScopeId(),
    ScratchesNamedScope.ID
  );
  private static final List<String> ALLOWED_SEVERITIES = ContainerUtil.concat(List.of("custom", "TYPO"), ContainerUtil.map(HighlightSeverity.DEFAULT_SEVERITIES, severity -> severity.getName()));

  private static final StringEventField INSPECTION_ID_FIELD =
    EventFields.StringValidatedByCustomRule("inspection_id", InspectionToolValidator.class);
  private static final StringEventField SCOPE_FIELD = EventFields.String("scope", ALLOWED_SCOPES);
  private static final StringEventField SEVERITY_FIELD = EventFields.String("severity", ALLOWED_SEVERITIES);
  private static final BooleanEventField ENABLED_FIELD = EventFields.Boolean("enabled");
  private static final BooleanEventField INSPECTION_ENABLED_FIELD = EventFields.Boolean("inspection_enabled");
  private static final PrimitiveEventField<Object> OPTION_VALUE_FIELD = new PrimitiveEventField<>() {
    @NotNull
    @Override
    public List<String> getValidationRule() {
      return Arrays.asList("{enum#boolean}", "{regexp#integer}");
    }

    @Override
    public void addData(@NotNull FeatureUsageData fuData, Object value) {
      if (value instanceof Integer) {
        fuData.addData(getName(), (Integer)value);
      } else if (value instanceof Boolean) {
        fuData.addData(getName(), (Boolean)value);
      }
    }

    @NotNull
    @Override
    public String getName() {
      return "option_value";
    }
  };
  private static final StringEventField OPTION_TYPE_FIELD =
    new StringEventField.ValidatedByAllowedValues("option_type", Arrays.asList("boolean", "integer"));
  private static final StringEventField OPTION_NAME_FIELD =
    EventFields.StringValidatedByCustomRule("option_name", PluginInfoValidationRule.class);
  private static final EventLogGroup GROUP = new EventLogGroup("inspections", 14);

  private static final VarargEventId NOT_DEFAULT_STATE =
    GROUP.registerVarargEvent("not.default.state",
                              INSPECTION_ID_FIELD,
                              EventFields.Language,
                              ENABLED_FIELD,
                              EventFields.PluginInfo);
  private static final VarargEventId SETTING_NON_DEFAULT_STATE =
    GROUP.registerVarargEvent("setting.non.default.state",
                              INSPECTION_ID_FIELD,
                              INSPECTION_ENABLED_FIELD,
                              EventFields.PluginInfo,
                              OPTION_TYPE_FIELD,
                              OPTION_VALUE_FIELD,
                              OPTION_NAME_FIELD);
  private static final EventId1<Integer> PROFILES =
    GROUP.registerEvent("profiles",
                        EventFields.Int("amount"));
  private static final EventId3<Boolean, Boolean, Boolean> PROFILE =
    GROUP.registerEvent("used.profile",
                        EventFields.Boolean("project_level"),
                        EventFields.Boolean("default"),
                        EventFields.Boolean("locked"));
  private static final VarargEventId NOT_DEFAULT_SCOPE_AND_SEVERITY =
    GROUP.registerVarargEvent("not.default.scope.and.severity",
                              INSPECTION_ID_FIELD,
                              SCOPE_FIELD,
                              SEVERITY_FIELD,
                              EventFields.PluginInfo);

  private static final StringListEventField INSPECTION_IDS_REPORTING_PROBLEMS_FIELD = EventFields.StringListValidatedByCustomRule(
    "inspectionIds", InspectionToolValidator.class);

  @SuppressWarnings("TypeParameterExtendsFinalClass")
  private static final EventId2<List<? extends String>, Integer> INSPECTION_IDS_REPORTING_PROBLEMS = GROUP.registerEvent(
    "inspections.reporting.problems",
    INSPECTION_IDS_REPORTING_PROBLEMS_FIELD,
    EventFields.Int("inspectionSessions")
  );

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics(@NotNull final Project project) {
    final Set<MetricEvent> result = new HashSet<>();

    final var profileManager = InspectionProjectProfileManager.getInstance(project);
    result.add(PROFILES.metric(profileManager.getProfiles().size()));
    final var profile = profileManager.getCurrentProfile();
    result.add(create(profile));

    final List<ScopeToolState> tools = profile.getAllTools();
    for (ScopeToolState state : tools) {
      InspectionToolWrapper<?, ?> tool = state.getTool();
      PluginInfo pluginInfo = getInfo(tool);

      // not.default.state
      if (ENABLED.test(state)) {
        result.add(create(tool, pluginInfo, true));
      }
      else if (DISABLED.test(state)) {
        result.add(create(tool, pluginInfo, false));
      }

      // setting.non.default.state
      result.addAll(getChangedSettingsEvents(tool, pluginInfo, state.isEnabled()));

      // not.default.scope.and.severity
      final MetricEvent scopeAndSeverityEvent = getChangedScopeAndSeverityEvent(state, pluginInfo);
      if (scopeAndSeverityEvent != null) result.add(scopeAndSeverityEvent);
    }

    result.add(getInspectionsReportingProblemsEvent(project));

    return result;
  }

  private static MetricEvent getInspectionsReportingProblemsEvent(@NotNull Project project) {
    InspectionUsageStorage.Report report = InspectionUsageStorage.getInstance(project).collectHighligtingReport();
    return INSPECTION_IDS_REPORTING_PROBLEMS.metric(new ArrayList<>(report.inspectionsReportingProblems()), report.inspectionSessionCount());
  }

  private static Collection<MetricEvent> getChangedSettingsEvents(InspectionToolWrapper<?, ?> tool,
                                                                  PluginInfo pluginInfo,
                                                                  boolean inspectionEnabled) {
    if (!isSafeToReport(pluginInfo)) {
      return Collections.emptyList();
    }

    if (!tool.isInitialized()) {
      // trade-off : we would like to not trigger class loading and instantiation of unnecessary inspections
      return Collections.emptyList();
    }

    InspectionProfileEntry entry = tool.getTool();
    Map<String, Attribute> options = getOptions(entry);
    if (options.isEmpty()) {
      return Collections.emptyList();
    }

    Set<String> fields = ContainerUtil.map2Set(ReflectionUtil.collectFields(entry.getClass()), f -> f.getName());
    Map<String, Attribute> defaultOptions = getOptions(ReflectionUtil.newInstance(entry.getClass()));

    Collection<MetricEvent> result = new ArrayList<>();
    String inspectionId = tool.getID();
    for (Map.Entry<String, Attribute> option : options.entrySet()) {
      String name = option.getKey();
      Attribute value = option.getValue();
      if (fields.contains(name) && value != null) {
        Attribute defaultValue = defaultOptions.get(name);
        if (defaultValue == null || !StringUtil.equals(value.getValue(), defaultValue.getValue())) {
          final var settingPair = getSettingValue(value);
          if (settingPair == null) continue;
          result.add(SETTING_NON_DEFAULT_STATE.metric(
            INSPECTION_ID_FIELD.with(inspectionId),
            INSPECTION_ENABLED_FIELD.with(inspectionEnabled),
            EventFields.PluginInfo.with(pluginInfo),
            settingPair.first,
            settingPair.second,
            OPTION_NAME_FIELD.with(name)
          ));
        }
      }
    }
    return result;
  }

  private static Pair<EventPair<String>, EventPair<Object>> getSettingValue(Attribute value) {
    try {
      final boolean booleanValue = value.getBooleanValue();
      return new Pair<>(OPTION_TYPE_FIELD.with("boolean"), OPTION_VALUE_FIELD.with(booleanValue));
    }
    catch (DataConversionException e1) {
      try {
        final int intValue = value.getIntValue();
        return new Pair<>(OPTION_TYPE_FIELD.with("integer"), OPTION_VALUE_FIELD.with(intValue));
      }
      catch (DataConversionException e2) {
        return null;
      }
    }
  }

  @NotNull
  private static MetricEvent create(InspectionToolWrapper<?, ?> tool, PluginInfo info, boolean enabled) {
    return NOT_DEFAULT_STATE.metric(
      INSPECTION_ID_FIELD.with(isSafeToReport(info) ? tool.getID() : "third.party"),
      EventFields.Language.with(Language.findLanguageByID(tool.getLanguage())),
      ENABLED_FIELD.with(enabled),
      EventFields.PluginInfo.with(info)
    );
  }

  @NotNull
  private static MetricEvent create(InspectionProfileImpl profile) {
    return PROFILE.metric(
      profile.isProjectLevel(),
      profile.toString().equals(profile.isProjectLevel() ? "Project Default" : "Default"),
      profile.isProfileLocked()
    );
  }

  private static boolean isSafeToReport(PluginInfo info) {
    return info != null && info.isSafeToReport();
  }

  @Nullable
  private static PluginInfo getInfo(InspectionToolWrapper<?, ?> tool) {
    InspectionEP extension = tool.getExtension();
    PluginDescriptor pluginDescriptor = extension == null ? null : extension.getPluginDescriptor();
    return pluginDescriptor != null ? PluginInfoDetectorKt.getPluginInfoByDescriptor(pluginDescriptor) : null;
  }

  private static Map<String, Attribute> getOptions(InspectionProfileEntry entry) {
    Element element = new Element("options");
    try {
      ScopeToolState.tryWriteSettings(entry, element);
      List<Content> options = element.getContent();
      if (options.isEmpty()) {
        return Collections.emptyMap();
      }

      Map<String, Attribute> set = new HashMap<>(options.size());
      for (Content option : options) {
        if (option instanceof Element el) {
          Attribute nameAttr = el.getAttribute("name");
          Attribute valueAttr = el.getAttribute("value");
          if (nameAttr != null && valueAttr != null) {
            set.put(nameAttr.getValue(), valueAttr);
          }
        }
      }
      return set;
    }
    catch (Exception e) {
      return Collections.emptyMap();
    }
  }

  @Nullable
  private static MetricEvent getChangedScopeAndSeverityEvent(ScopeToolState tool, PluginInfo info) {
    if (!isSafeToReport(info)) {
      return null;
    }

    if (!(tool.getScopeName().equals(CustomScopesProviderEx.getAllScope().getScopeId()) &&
          tool.getLevel().getSeverity().getName().equals(tool.getTool().getDefaultLevel().getSeverity().getName()))) {
      final String scopeId = tool.getScopeName();
      final String severity = tool.getLevel().getName();

      return NOT_DEFAULT_SCOPE_AND_SEVERITY.metric(
        INSPECTION_ID_FIELD.with(isSafeToReport(info) ? tool.getTool().getID() : "third.party"),
        SCOPE_FIELD.with(ALLOWED_SCOPES.contains(scopeId) ? scopeId : "custom"),
        SEVERITY_FIELD.with(ALLOWED_SEVERITIES.contains(severity) ? severity : "custom"),
        EventFields.PluginInfo.with(info)
      );
    }

    return null;
  }

  public static class InspectionToolValidator extends CustomValidationRule {
    @NotNull
    @Override
    public String getRuleId() {
      return "tool";
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if (isThirdPartyValue(data)) return ValidationResultType.ACCEPTED;
      return acceptWhenReportedByPluginFromPluginRepository(context);
    }
  }
}