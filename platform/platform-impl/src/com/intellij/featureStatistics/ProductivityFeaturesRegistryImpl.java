// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.Function;
import com.intellij.util.ResourceUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.*;

public final class ProductivityFeaturesRegistryImpl extends ProductivityFeaturesRegistry {
  private static final Logger LOG = Logger.getInstance(ProductivityFeaturesRegistryImpl.class);

  private final Map<String, FeatureDescriptor> myFeatures = new HashMap<>();
  private final List<FeatureUsageEvent.Action> myActionEvents = new ArrayList<>();
  private final List<FeatureUsageEvent.Intention> myIntentionEvents = new ArrayList<>();
  private final Map<String, GroupDescriptor> myGroups = new HashMap<>();
  private final List<Pair<String, ApplicabilityFilter>> myApplicabilityFilters = new ArrayList<>();

  private boolean myAdditionalFeaturesLoaded;

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
    String path = "ProductivityFeaturesRegistry.xml";
    boolean found;
    try {
      found = readFromXml(path);
    }
    catch (Throwable e) {
      LOG.error(e);
      found = false;
    }
    if (!found && !ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.error(path + " not found");
    }

    try {
      readFromXml("IdeSpecificFeatures.xml");
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  private boolean readFromXml(@NotNull @NonNls String path) throws JDOMException, IOException {
    return readFromXml(path, ProductivityFeaturesRegistryImpl.class.getClassLoader());
  }

  private boolean readFromXml(@NotNull String path, @NotNull ClassLoader classLoader) throws JDOMException, IOException {
    byte[] data = ResourceUtil.getResourceAsBytes(path, classLoader, true);
    if (data == null) {
      return false;
    }

    Element root = JDOMUtil.load(data);
    for (Element groupElement : root.getChildren(TAG_GROUP)) {
      readGroup(groupElement);
    }
    readFilters(root, classLoader);
    return true;
  }

  private void lazyLoadFromPluginsFeaturesProviders() {
    if (myAdditionalFeaturesLoaded) {
      return;
    }

    myAdditionalFeaturesLoaded = true;
    ProductivityFeaturesProvider.EP_NAME.processWithPluginDescriptor((provider, pluginDescriptor) -> {
      for (String xmlUrl : provider.getXmlFilesUrls()) {
        try {
          readFromXml(Strings.trimStart(xmlUrl, "/"), pluginDescriptor.getClassLoader());
        }
        catch (Exception e) {
          LOG.error(new PluginException("Error while reading " + xmlUrl + " from " + provider + ": " + e.getMessage(),
                                        pluginDescriptor.getPluginId()));
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
          addUsageEvents(featureDescriptor);
        }
      }
      final ApplicabilityFilter[] applicabilityFilters = provider.getApplicabilityFilters();
      if (applicabilityFilters != null) {
        for (ApplicabilityFilter applicabilityFilter : applicabilityFilters) {
          myApplicabilityFilters.add(Pair.create(applicabilityFilter.getPrefix(), applicabilityFilter));
        }
      }
    });
  }

  private void readFilters(Element element, @NotNull ClassLoader classLoader) {
    for (Element filterElement : element.getChildren(TAG_FILTER)) {
      String className = filterElement.getAttributeValue(CLASS_ATTR);
      try {
        Class<?> aClass = classLoader.loadClass(className);
        if (!ApplicabilityFilter.class.isAssignableFrom(aClass)) {
          LOG.error("filter class must implement com.intellij.featureStatistics.ApplicabilityFilter");
          continue;
        }

        ApplicabilityFilter filter = (ApplicabilityFilter)aClass.getDeclaredConstructor().newInstance();
        myApplicabilityFilters.add(new Pair<>(filterElement.getAttributeValue(PREFIX_ATTR), filter));
      }
      catch (Exception e) {
        LOG.error("Cannot instantiate filter " + className, e);
      }
    }
  }

  private void addUsageEvents(FeatureDescriptor featureDescriptor) {
    myActionEvents.addAll(featureDescriptor.getActionEvents());
    myIntentionEvents.addAll(featureDescriptor.getIntentionEvents());
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
      addUsageEvents(featureDescriptor);
    }
  }

  private @Nullable <T extends FeatureUsageEvent> FeatureDescriptor findFeatureByEvent(List<T> events, Function<T, Boolean> eventChecker) {
    lazyLoadFromPluginsFeaturesProviders();
    return events
      .stream()
      .filter(e -> eventChecker.fun(e))
      .findFirst()
      .map(e -> getFeatureDescriptorEx(e.featureId()))
      .orElse(null);
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
  public @Nullable FeatureDescriptor findFeatureByAction(@NotNull String actionId) {
    return findFeatureByEvent(myActionEvents, action -> actionId.equals(action.getActionId()));
  }

  @Override
  public @Nullable FeatureDescriptor findFeatureByIntention(@NotNull Class<?> intentionClass) {
    return findFeatureByEvent(myIntentionEvents, intention -> intentionClass.getName().equals(intention.getIntentionClassName()));
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
}
