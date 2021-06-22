// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics;

import com.intellij.internal.statistic.eventLog.EventLogListenersManager;
import com.intellij.internal.statistic.eventLog.EventLogSystemLogger;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProductivityFeaturesRegistryImpl extends ProductivityFeaturesRegistry {
  private static final Logger LOG = Logger.getInstance(ProductivityFeaturesRegistry.class);
  private final Map<String, FeatureDescriptor> myFeatures = new HashMap<>();
  private final Map<String, Map<String, List<LogEventDetector>>> myLogEventDetectors = new HashMap<>();
  private final Map<String, GroupDescriptor> myGroups = new HashMap<>();
  private final List<Pair<String, ApplicabilityFilter>> myApplicabilityFilters = new ArrayList<>();

  private boolean myAdditionalFeaturesLoaded = false;

  @NonNls public static final String WELCOME = "features.welcome";

  @NonNls private static final String TAG_FILTER = "filter";
  @NonNls private static final String TAG_GROUP = "group";
  @NonNls private static final String TAG_FEATURE = "feature";
  @NonNls private static final String TODO_HTML_MARKER = "todo.html";
  @NonNls private static final String CLASS_ATTR = "class";
  @NonNls private static final String PREFIX_ATTR = "prefix";

  public ProductivityFeaturesRegistryImpl() {
    reloadFromXml();
  }

  private void reloadFromXml() {
    try {
      readFromXml("/ProductivityFeaturesRegistry.xml");
    }
    catch (FileNotFoundException e) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.error(e);
      }
    }
    catch (Throwable e) {
      LOG.error(e);
    }

    try {
      readFromXml("/IdeSpecificFeatures.xml");
    }
    catch (FileNotFoundException ignore) {
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  private void readFromXml(@NotNull @NonNls String path) throws JDOMException, IOException {
    readFromXml(path, ProductivityFeaturesRegistryImpl.class);
  }

  private void readFromXml(@NotNull String path, @NotNull Class<?> clazz) throws JDOMException, IOException {
    Element root = JDOMUtil.load(clazz, path);
    for (Element groupElement : root.getChildren(TAG_GROUP)) {
      readGroup(groupElement);
    }
    readFilters(root);
  }

  private void lazyLoadFromPluginsFeaturesProviders() {
    if (myAdditionalFeaturesLoaded) {
      return;
    }

    myAdditionalFeaturesLoaded = true;
    loadFeaturesFromProviders(ProductivityFeaturesProvider.EP_NAME.getExtensionList());
  }

  private void loadFeaturesFromProviders(@NotNull List<? extends ProductivityFeaturesProvider> providers) {
    for (ProductivityFeaturesProvider provider : providers) {
      for (String xmlUrl : provider.getXmlFilesUrls()) {
        try {
          readFromXml(xmlUrl, provider.getClass());
        }
        catch (Exception e) {
          LOG.error("Error while reading " + xmlUrl + " from " + provider + ": " + e.getMessage());
        }
      }

      final GroupDescriptor[] groupDescriptors = provider.getGroupDescriptors();
      if (groupDescriptors != null) {
        for (GroupDescriptor groupDescriptor : groupDescriptors) {
          myGroups.put(groupDescriptor.getId(), groupDescriptor);
        }
      }
      final FeatureDescriptor[] featureDescriptors = provider.getFeatureDescriptors();
      if (featureDescriptors != null) {
        for (FeatureDescriptor featureDescriptor : featureDescriptors) {
          final FeatureDescriptor featureLoadedStatistics = myFeatures.get(featureDescriptor.getId());
          if (featureLoadedStatistics != null) {
            featureDescriptor.copyStatistics(featureLoadedStatistics);
          }
          myFeatures.put(featureDescriptor.getId(), featureDescriptor);
          addLogEventDetectors(featureDescriptor);
        }
      }
      final ApplicabilityFilter[] applicabilityFilters = provider.getApplicabilityFilters();
      if (applicabilityFilters != null) {
        for (ApplicabilityFilter applicabilityFilter : applicabilityFilters) {
          myApplicabilityFilters.add(Pair.create(applicabilityFilter.getPrefix(), applicabilityFilter));
        }
      }
    }
  }

  private void readFilters(Element element) {
    for (Element filterElement : element.getChildren(TAG_FILTER)) {
      String className = filterElement.getAttributeValue(CLASS_ATTR);
      try {
        Class klass = Class.forName(className);
        if (!ApplicabilityFilter.class.isAssignableFrom(klass)) {
          LOG.error("filter class must implement com.intellij.featureStatistics.ApplicabilityFilter");
          continue;
        }

        ApplicabilityFilter filter = (ApplicabilityFilter)klass.newInstance();
        myApplicabilityFilters.add(Pair.create(filterElement.getAttributeValue(PREFIX_ATTR), filter));
      }
      catch (Exception e) {
        LOG.error("Cannot instantiate filter " + className, e);
      }
    }
  }

  private void addLogEventDetectors(FeatureDescriptor featureDescriptor) {
    var featureDetectors = featureDescriptor.getLogEventDetectors();
    for (LogEventDetector detector : featureDetectors) {
      var event2detectors = myLogEventDetectors.computeIfAbsent(detector.groupId(), s -> new HashMap<>());
      var detectors = event2detectors.computeIfAbsent(detector.eventId(), s -> new ArrayList<>());
      detectors.add(detector);
    }
  }

  private void readGroup(Element groupElement) {
    GroupDescriptor groupDescriptor = new GroupDescriptor();
    groupDescriptor.readExternal(groupElement);
    String groupId = groupDescriptor.getId();
    myGroups.put(groupId, groupDescriptor);
    readFeatures(groupElement, groupDescriptor);
  }

  private void readFeatures(Element groupElement, GroupDescriptor groupDescriptor) {
    for (Element featureElement : groupElement.getChildren(TAG_FEATURE)) {
      FeatureDescriptor featureDescriptor = new FeatureDescriptor(groupDescriptor, featureElement);
      if (!TODO_HTML_MARKER.equals(featureDescriptor.getTipFileName())) {
        myFeatures.put(featureDescriptor.getId(), featureDescriptor);
      }
      addLogEventDetectors(featureDescriptor);
    }
  }

  private @Nullable FeatureDescriptor getFeatureDescriptorByLogEvent(@NotNull String groupId,
                                                                     @NotNull String eventId,
                                                                     @NotNull Map<String, Object> eventData) {
    lazyLoadFromPluginsFeaturesProviders();
    var event2detectors = myLogEventDetectors.get(groupId);
    if (event2detectors != null) {
      var detectors = event2detectors.get(eventId);
      if (detectors != null) {
        var detector = detectors.stream().filter(d -> d.succeed(eventData)).findFirst();
        if (detector.isPresent()) {
          return getFeatureDescriptorEx(detector.get().featureId());
        }
      }
    }
    return null;
  }

  @Override
  @NotNull
  public Set<String> getFeatureIds() {
    lazyLoadFromPluginsFeaturesProviders();
    return myFeatures.keySet();
  }

  @Override
  public FeatureDescriptor getFeatureDescriptor(@NotNull String id) {
    lazyLoadFromPluginsFeaturesProviders();
    return getFeatureDescriptorEx(id);
  }

  public FeatureDescriptor getFeatureDescriptorEx(@NotNull String id) {
    if (WELCOME.equals(id)) {
      return new FeatureDescriptor(WELCOME, "AdaptiveWelcome.html", FeatureStatisticsBundle.message("feature.statistics.welcome.tip.name"));
    }
    return myFeatures.get(id);
  }

  @Override
  public GroupDescriptor getGroupDescriptor(@NotNull String id) {
    lazyLoadFromPluginsFeaturesProviders();
    return myGroups.get(id);
  }

  @Override
  public ApplicabilityFilter @NotNull [] getMatchingFilters(@NotNull String featureId) {
    lazyLoadFromPluginsFeaturesProviders();
    List<ApplicabilityFilter> filters = new ArrayList<>();
    for (Pair<String, ApplicabilityFilter> pair : myApplicabilityFilters) {
      if (featureId.startsWith(pair.getFirst())) {
        filters.add(pair.getSecond());
      }
    }
    return filters.toArray(new ApplicabilityFilter[0]);
  }

  @Override
  @NonNls
  public String toString() {
    return super.toString() + "; myAdditionalFeaturesLoaded=" + myAdditionalFeaturesLoaded;
  }

  @TestOnly
  public void prepareForTest() {
    myAdditionalFeaturesLoaded = false;
    myFeatures.clear();
    myApplicabilityFilters.clear();
    myGroups.clear();
    reloadFromXml();
  }

  public static class EventLogSubscribeActivity implements StartupActivity.DumbAware {
    private static final AtomicBoolean isSubscribed = new AtomicBoolean(false);

    @Override
    public void runActivity(@NotNull Project project) {
      if (ApplicationManager.getApplication().isUnitTestMode()) return;
      ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
      if (registry instanceof ProductivityFeaturesRegistryImpl) {
        var registryImpl = (ProductivityFeaturesRegistryImpl)registry;
        FeatureUsageTracker usageTracker = FeatureUsageTracker.getInstance();
        if (usageTracker != null && !isSubscribed.getAndSet(true)) {
          ApplicationManager.getApplication().getService(EventLogListenersManager.class).subscribe((logEvent, rawEventId, rawData) -> {
            FeatureDescriptor feature = registryImpl.getFeatureDescriptorByLogEvent(logEvent.getGroup().getId(),
                                                                                    logEvent.getEvent().getId(),
                                                                                    logEvent.getEvent().getData());
            if (feature != null) {
              usageTracker.triggerFeatureUsed(feature.getId());
            }
          }, EventLogSystemLogger.DEFAULT_RECORDER);
        }
      } else {
        LOG.warn("No ProductivityFeaturesRegistry implementation. Features from Event Log won't be recorded");
      }
    }
  }
}
