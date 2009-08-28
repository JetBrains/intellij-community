/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.featureStatistics;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"})
@State(
    name = "FeatureUsageStatistics",
    storages = {@Storage(
        id = "other",
        file = "$APP_CONFIG$/feature.usage.statistics.xml")})
public class FeatureUsageTrackerImpl extends FeatureUsageTracker implements PersistentStateComponent<Element> {
  private static final long DAY = 1000 * 60 * 60 * 24;
  private long FIRST_RUN_TIME = 0;
  boolean HAVE_BEEN_SHOWN = false;

  private final ProductivityFeaturesRegistry myRegistry;

  @NonNls private static final String FEATURE_TAG = "feature";
  @NonNls private static final String ATT_SHOW_IN_OTHER = "show-in-other";
  @NonNls private static final String ATT_SHOW_IN_COMPILATION = "show-in-compilation";
  @NonNls private static final String ATT_ID = "id";
  @NonNls private static final String ATT_FIRST_RUN = "first-run";
  @NonNls private static final String ATT_HAVE_BEEN_SHOWN = "have-been-shown";

  public FeatureUsageTrackerImpl(ProductivityFeaturesRegistry productivityFeaturesRegistry) {
    myRegistry = productivityFeaturesRegistry;
  }

  String[] getFeaturesToShow(Project project) {
    List<String> result = new ArrayList<String>();
    for (String id : ProductivityFeaturesRegistry.getInstance().getFeatureIds()) {
      if (isToBeShown(id, project)) {
        result.add(id);
      }
    }
    return ArrayUtil.toStringArray(result);
  }

  public boolean isToBeShown(String featureId, Project project) {
    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    FeatureDescriptor descriptor = registry.getFeatureDescriptor(featureId);
    if (descriptor == null || !descriptor.isUnused()) return false;

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
    long succesive_interval = descriptor.getDaysBetweenSuccesiveShowUps() * DAY + descriptor.getShownCount() * 2;
    long firstShowUpInterval = descriptor.getDaysBeforeFirstShowUp() * DAY;
    long lastTimeUsed = descriptor.getLastTimeUsed();
    long lastTimeShown = descriptor.getLastTimeShown();
    return lastTimeShown == 0 && firstShowUpInterval + getFirstRunTime() < current ||
           lastTimeShown > 0 && current - lastTimeShown > succesive_interval && current - lastTimeUsed > succesive_interval;
  }

  public long getFirstRunTime() {
    if (FIRST_RUN_TIME == 0) {
      FIRST_RUN_TIME = System.currentTimeMillis();
    }
    return FIRST_RUN_TIME;
  }

  public void loadState(final Element element) {
    List featuresList = element.getChildren(FEATURE_TAG);
    for (Object aFeaturesList : featuresList) {
      Element featureElement = (Element)aFeaturesList;
      FeatureDescriptor descriptor =
        ((ProductivityFeaturesRegistryImpl)myRegistry).getFeatureDescriptorEx(featureElement.getAttributeValue(ATT_ID));
      if (descriptor != null) {
        descriptor.readStatistics(featureElement);
      }
    }

    try {
      FIRST_RUN_TIME = Long.parseLong(element.getAttributeValue(ATT_FIRST_RUN));
    }
    catch (NumberFormatException e) {
      FIRST_RUN_TIME = 0;
    }

    HAVE_BEEN_SHOWN = Boolean.valueOf(element.getAttributeValue(ATT_HAVE_BEEN_SHOWN)).booleanValue();
    SHOW_IN_OTHER_PROGRESS = Boolean.valueOf(element.getAttributeValue(ATT_SHOW_IN_OTHER, Boolean.toString(true))).booleanValue();
    SHOW_IN_COMPILATION_PROGRESS = Boolean.valueOf(element.getAttributeValue(ATT_SHOW_IN_COMPILATION, Boolean.toString(true))).booleanValue();
  }

  public Element getState() {
    Element element = new Element("state");
    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    Set<String> ids = registry.getFeatureIds();
    for (String id: ids) {
      Element featureElement = new Element(FEATURE_TAG);
      featureElement.setAttribute(ATT_ID, id);
      FeatureDescriptor descriptor = registry.getFeatureDescriptor(id);
      descriptor.writeStatistics(featureElement);
      element.addContent(featureElement);
    }

    element.setAttribute(ATT_FIRST_RUN, String.valueOf(getFirstRunTime()));
    element.setAttribute(ATT_HAVE_BEEN_SHOWN, String.valueOf(HAVE_BEEN_SHOWN));
    element.setAttribute(ATT_SHOW_IN_OTHER, String.valueOf(SHOW_IN_OTHER_PROGRESS));
    element.setAttribute(ATT_SHOW_IN_COMPILATION, String.valueOf(SHOW_IN_COMPILATION_PROGRESS));

    return element;
  }

  public void triggerFeatureUsed(String featureId) {
    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    FeatureDescriptor descriptor = registry.getFeatureDescriptor(featureId);
    if (descriptor == null) {
     // TODO: LOG.error("Feature '" + featureId +"' must be registered prior triggerFeatureUsed() is called");
    }
    else {
      descriptor.triggerUsed();
    }
  }

  public void triggerFeatureShown(String featureId) {
    FeatureDescriptor descriptor = ProductivityFeaturesRegistry.getInstance().getFeatureDescriptor(featureId);
    if (descriptor != null) {
      descriptor.triggerShown();
    }
  }

}
