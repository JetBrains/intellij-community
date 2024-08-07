// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId3;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.local.ActionExtendedSummary;
import com.intellij.internal.statistic.local.ActionsLocalSummary;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static com.intellij.configurationStore.XmlSerializer.getJdomSerializer;
import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPluginInfo;

@State(
  name = "FeatureUsageStatistics",
  storages = {
    @Storage(value = FeatureUsageTrackerImpl.FEATURES_USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED, usePathMacroManager = false),
    @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED, deprecated = true)
  }
)
public final class FeatureUsageTrackerImpl extends FeatureUsageTracker implements PersistentStateComponent<Element> {
  public static final String FEATURES_USAGE_STATISTICS_XML = "features.usage.statistics.xml";

  private static final int HOUR = 1000 * 60 * 60;
  private static final long DAY = HOUR * 24;
  private long FIRST_RUN_TIME = 0;
  private CompletionStatistics myCompletionStats = new CompletionStatistics();
  private CumulativeStatistics myFixesStats = new CumulativeStatistics();
  boolean HAVE_BEEN_SHOWN = false;

  private static final @NonNls String FEATURE_TAG = "feature";
  private static final @NonNls String ATT_SHOW_IN_OTHER = "show-in-other";
  private static final @NonNls String ATT_SHOW_IN_COMPILATION = "show-in-compilation";
  private static final @NonNls String ATT_ID = "id";
  private static final @NonNls String ATT_FIRST_RUN = "first-run";
  private static final @NonNls String COMPLETION_STATS_TAG = "completionStatsTag";
  private static final @NonNls String FIXES_STATS_TAG = "fixesStatsTag";
  private static final @NonNls String ATT_HAVE_BEEN_SHOWN = "have-been-shown";

  @Override
  public boolean isToBeShown(String featureId, Project project) {
    return isToBeShown(featureId, project, DAY);
  }

  private boolean isToBeShown(String featureId, Project project, final long timeUnit) {
    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    if (registry == null) return false;
    FeatureDescriptor descriptor = registry.getFeatureDescriptor(featureId);
    if (descriptor == null || !descriptor.isUnused()) {
      return false;
    }

    String[] dependencyFeatures = descriptor.getDependencyFeatures();
    boolean locked = dependencyFeatures.length > 0;
    for (int i = 0; locked && i < dependencyFeatures.length; i++) {
      if (!registry.getFeatureDescriptor(dependencyFeatures[i]).isUnused()) {
        locked = false;
      }
    }
    if (locked) return false;

    ApplicabilityFilter[] filters = registry.getMatchingFilters(featureId);
    for (ApplicabilityFilter filter: filters) {
      if (!filter.isApplicable(featureId, project)) return false;
    }

    long current = System.currentTimeMillis();
    long succesive_interval = descriptor.getDaysBetweenSuccessiveShowUps() * timeUnit + descriptor.getShownCount() * 2L;
    long firstShowUpInterval = descriptor.getDaysBeforeFirstShowUp() * timeUnit;
    long lastTimeUsed = descriptor.getLastTimeUsed();
    long lastTimeShown = descriptor.getLastTimeShown();
    return lastTimeShown == 0 && firstShowUpInterval + getFirstRunTime() < current ||
           lastTimeShown > 0 && current - lastTimeShown > succesive_interval && current - lastTimeUsed > succesive_interval;
  }

  @Override
  public boolean isToBeAdvertisedInLookup(@NonNls String featureId, Project project) {
    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    if (registry != null) {
      FeatureDescriptor descriptor = registry.getFeatureDescriptor(featureId);
      if (descriptor != null && System.currentTimeMillis() - descriptor.getLastTimeUsed() > 10 * DAY) {
        return true;
      }
    }

    return isToBeShown(featureId, project, HOUR);
  }

  public @NotNull CompletionStatistics getCompletionStatistics() {
    return myCompletionStats;
  }

  public CumulativeStatistics getFixesStats() {
    return myFixesStats;
  }

  public long getFirstRunTime() {
    if (FIRST_RUN_TIME == 0) {
      FIRST_RUN_TIME = System.currentTimeMillis();
    }
    return FIRST_RUN_TIME;
  }

  @Override
  public void loadState(@NotNull Element element) {
    List<Element> featuresList = element.getChildren(FEATURE_TAG);
    if (!featuresList.isEmpty()) {
      ProductivityFeaturesRegistry featureRegistry = ProductivityFeaturesRegistry.getInstance();
      if (featureRegistry != null) {
        for (Element itemElement : featuresList) {
          FeatureDescriptor descriptor = featureRegistry.getFeatureDescriptor(itemElement.getAttributeValue(ATT_ID));
          if (descriptor != null) {
            descriptor.readStatistics(itemElement);
          }
        }
      }
    }
    adjustFeatureUsageInfo();

    try {
      FIRST_RUN_TIME = Long.parseLong(element.getAttributeValue(ATT_FIRST_RUN));
    }
    catch (NumberFormatException e) {
      FIRST_RUN_TIME = 0;
    }

    Element stats = element.getChild(COMPLETION_STATS_TAG);
    if (stats != null) {
      myCompletionStats = XmlSerializer.deserialize(stats, CompletionStatistics.class);
    }

    Element fStats = element.getChild(FIXES_STATS_TAG);
    if (fStats != null) {
      myFixesStats = XmlSerializer.deserialize(fStats, CumulativeStatistics.class);
    }

    HAVE_BEEN_SHOWN = Boolean.valueOf(element.getAttributeValue(ATT_HAVE_BEEN_SHOWN)).booleanValue();
    SHOW_IN_OTHER_PROGRESS = Boolean.valueOf(element.getAttributeValue(ATT_SHOW_IN_OTHER, Boolean.toString(true))).booleanValue();
    SHOW_IN_COMPILATION_PROGRESS = Boolean.valueOf(element.getAttributeValue(ATT_SHOW_IN_COMPILATION, Boolean.toString(true))).booleanValue();
  }

  private static void adjustFeatureUsageInfo() {
    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    if (registry == null) return;

    ActionsLocalSummary actionsSummary = ApplicationManager.getApplication().getService(ActionsLocalSummary.class);
    registry.getFeatureIds().stream()
      .map(id -> registry.getFeatureDescriptor(id))
      .forEach(feature -> {
        List<ActionExtendedSummary> summary = ContainerUtil.mapNotNull(feature.getActionEvents(),
                                                                       action -> actionsSummary.getActionStatsById(action.getActionId()));
        int adjustedUsageCount = summary.stream().map(s -> s.usageCount).reduce(Integer::sum).orElse(0);
        long adjustedLastTimeUsed = summary.stream().map(s -> s.lastUsedTimestamp).max(Long::compare).orElse(0L);
        feature.adjustUsageInfo(adjustedUsageCount, adjustedLastTimeUsed);
      });
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    if (registry != null) {
      Set<String> ids = registry.getFeatureIds();
      for (String id: ids) {
        Element featureElement = new Element(FEATURE_TAG);
        featureElement.setAttribute(ATT_ID, id);
        FeatureDescriptor descriptor = registry.getFeatureDescriptor(id);
        descriptor.writeStatistics(featureElement);
        element.addContent(featureElement);
      }
    }

    Element statsTag = new Element(COMPLETION_STATS_TAG);
    getJdomSerializer().serializeObjectInto(myCompletionStats, statsTag, getJdomSerializer().getDefaultSerializationFilter());
    element.addContent(statsTag);

    Element fstatsTag = new Element(FIXES_STATS_TAG);
    getJdomSerializer().serializeObjectInto(myFixesStats, fstatsTag, getJdomSerializer().getDefaultSerializationFilter());
    element.addContent(fstatsTag);

    element.setAttribute(ATT_FIRST_RUN, String.valueOf(getFirstRunTime()));
    element.setAttribute(ATT_HAVE_BEEN_SHOWN, String.valueOf(HAVE_BEEN_SHOWN));
    element.setAttribute(ATT_SHOW_IN_OTHER, String.valueOf(SHOW_IN_OTHER_PROGRESS));
    element.setAttribute(ATT_SHOW_IN_COMPILATION, String.valueOf(SHOW_IN_COMPILATION_PROGRESS));

    return element;
  }

  @Override
  public void triggerFeatureUsed(@NotNull String featureId) {
    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    FeatureDescriptor descriptor = registry == null ? null : registry.getFeatureDescriptor(featureId);
    if (descriptor == null) {
      // TODO: LOG.error("Feature '" + featureId +"' must be registered prior triggerFeatureUsed() is called");
      return;
    }

    descriptor.triggerUsed();

    Class<? extends ProductivityFeaturesProvider> provider = descriptor.getProvider();
    String id = provider == null || getPluginInfo(provider).isDevelopedByJetBrains() ? descriptor.getId() : "third.party";
    ProductivityUsageCollector.FEATURE_USED.log(id, StringUtil.notNullize(descriptor.getGroupId(), "unknown"),
                                                provider == null ? null : getPluginInfo(provider));

    ApplicationManager.getApplication().getMessageBus().syncPublisher(FeaturesRegistryListener.TOPIC).featureUsed(descriptor);
  }

  @Override
  public void triggerFeatureUsedByAction(@NotNull String actionId) {
    triggerFeatureUsed(registry -> registry.findFeatureByAction(actionId));
  }

  @Override
  public void triggerFeatureUsedByIntention(@NotNull Class<?> intentionClass) {
    triggerFeatureUsed(registry -> registry.findFeatureByIntention(intentionClass));
  }

  @Override
  public void triggerFeatureShown(String featureId) {
    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    if (registry != null) {
      FeatureDescriptor descriptor = registry.getFeatureDescriptor(featureId);
      if (descriptor != null) {
        descriptor.triggerShown();
      }
    }
  }

  private static void triggerFeatureUsed(Function<? super ProductivityFeaturesRegistry, ? extends FeatureDescriptor> featureGetter) {
    ProductivityFeaturesRegistry featuresRegistry = ProductivityFeaturesRegistry.getInstance();
    if (featuresRegistry != null) {
      FeatureDescriptor feature = featureGetter.fun(featuresRegistry);
      if (feature != null) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(feature.getId());
      }
    }
  }

  @ApiStatus.Internal
  public static final class ProductivityUtilValidator extends CustomValidationRule {
    @Override
    public @NotNull String getRuleId() {
      return "productivity";
    }

    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return getRuleId().equals(ruleId) || "productivity_group".equals(ruleId);
    }

    @Override
    protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if (isThirdPartyValue(data)) return ValidationResultType.ACCEPTED;

      final String id = getEventDataField(context, "id");
      final String group = getEventDataField(context, "group");
      if (isValid(data, id, group)) {
        ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
        FeatureDescriptor descriptor = registry == null ? null : registry.getFeatureDescriptor(id);
        if (descriptor != null) {
          final String actualGroup = descriptor.getGroupId();
          if (StringUtil.equals(group, "unknown") || StringUtil.equals(group, actualGroup)) {
            final Class<? extends ProductivityFeaturesProvider> provider = descriptor.getProvider();
            final PluginInfo info =
              provider == null ? PluginInfoDetectorKt.getPlatformPlugin() : getPluginInfo(provider);
            return info.isDevelopedByJetBrains() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
          }
        }
      }
      return ValidationResultType.REJECTED;
    }

    private static boolean isValid(@NotNull String data, String id, String group) {
      return id != null && group != null && (data.equals(id) || data.equals(group));
    }
  }

  private static final class ProductivityUsageCollector extends CounterUsagesCollector {
    private static final EventLogGroup GROUP = new EventLogGroup("productivity", 59);
    private static final EventId3<String, String, PluginInfo> FEATURE_USED =
      GROUP.registerEvent("feature.used",
                          EventFields.StringValidatedByCustomRule("id", ProductivityUtilValidator.class),
                          EventFields.StringValidatedByCustomRule("group", ProductivityUtilValidator.class),
                          EventFields.PluginInfo);

    @Override
    public EventLogGroup getGroup() {
      return GROUP;
    }
  }
}
