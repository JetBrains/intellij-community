package com.intellij.featureStatistics;

import com.intellij.openapi.components.ApplicationComponent;

/**
 * User: anna
 * Date: Jan 30, 2005
 */
public abstract class ProductivityFeaturesProvider implements ApplicationComponent{
  
  public abstract FeatureDescriptor[] getFeatureDescriptors();

  public abstract GroupDescriptor[] getGroupDescriptors();

  public abstract ApplicabilityFilter[] getApplicabilityFilters();
}
