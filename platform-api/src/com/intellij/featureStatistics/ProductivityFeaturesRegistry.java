/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
