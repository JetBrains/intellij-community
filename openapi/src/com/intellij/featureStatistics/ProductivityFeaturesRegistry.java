package com.intellij.featureStatistics;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;

import java.util.Set;

/**
 * User: anna
 * Date: Feb 3, 2005
 */
public abstract class ProductivityFeaturesRegistry implements ApplicationComponent{
  public abstract Set<String> getFeatureIds();

  public abstract FeatureDescriptor getFeatureDescriptor(String id);

  public abstract GroupDescriptor getGroupDescriptor(String id);

  public abstract ApplicabilityFilter[] getMatchingFilters(String featureId);

  public static ProductivityFeaturesRegistry getInstance() {
    return ApplicationManager.getApplication().getComponent(ProductivityFeaturesRegistry.class);
  }
}
