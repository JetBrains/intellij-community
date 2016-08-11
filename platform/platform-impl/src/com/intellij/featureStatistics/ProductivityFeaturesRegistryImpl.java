/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class ProductivityFeaturesRegistryImpl extends ProductivityFeaturesRegistry {
  private static final Logger LOG = Logger.getInstance("#com.intellij.featureStatistics.ProductivityFeaturesRegistry");
  private final Map<String, FeatureDescriptor> myFeatures = new HashMap<>();
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
      readFromXml("file:///ProductivityFeaturesRegistry.xml");
    }
    catch (Exception e) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.error(e);
      }
    }

    try {
      readFromXml("file:///IdeSpecificFeatures.xml");
    }
    catch (Exception e) {// ignore
    }
  }

  private void readFromXml(String path) throws JDOMException, IOException {
    final Document document = JDOMUtil.loadResourceDocument(new URL(path));
    final Element root = document.getRootElement();
    readGroups(root);
    readFilters(root);
  }

  private void lazyLoadFromPluginsFeaturesProviders() {
    if (myAdditionalFeaturesLoaded) return;
    loadFeaturesFromProviders(ApplicationManager.getApplication().getComponents(ProductivityFeaturesProvider.class));
    loadFeaturesFromProviders(Extensions.getExtensions(ProductivityFeaturesProvider.EP_NAME));
    myAdditionalFeaturesLoaded = true;
  }

  private void loadFeaturesFromProviders(ProductivityFeaturesProvider[] providers) {
    for (ProductivityFeaturesProvider provider : providers) {
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
    List filters = element.getChildren(TAG_FILTER);
    for (Object filter1 : filters) {
      Element filterElement = (Element)filter1;
      String className = filterElement.getAttributeValue(CLASS_ATTR);
      try {
        Class klass = Class.forName(className);
        if (!ApplicabilityFilter.class.isAssignableFrom(klass)) {
          LOG.error("filter class must implement com.intellij.featureSatistics.ApplicabilityFilter");
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

  private void readGroups(Element element) {
    List groups = element.getChildren(TAG_GROUP);
    for (Object group : groups) {
      Element groupElement = (Element)group;
      readGroup(groupElement);
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
    List features = groupElement.getChildren(TAG_FEATURE);
    for (Object feature : features) {
      Element featureElement = (Element)feature;
      FeatureDescriptor featureDescriptor = new FeatureDescriptor(groupDescriptor);
      featureDescriptor.readExternal(featureElement);
      if (!TODO_HTML_MARKER.equals(featureDescriptor.getTipFileName())) {
        myFeatures.put(featureDescriptor.getId(), featureDescriptor);
      }
    }
  }

  @NotNull
  public Set<String> getFeatureIds() {
    lazyLoadFromPluginsFeaturesProviders();
    return myFeatures.keySet();
  }

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

  public GroupDescriptor getGroupDescriptor(@NotNull String id) {
    lazyLoadFromPluginsFeaturesProviders();
    return myGroups.get(id);
  }

  @NotNull
  public ApplicabilityFilter[] getMatchingFilters(@NotNull String featureId) {
    lazyLoadFromPluginsFeaturesProviders();
    List<ApplicabilityFilter> filters = new ArrayList<>();
    for (Pair<String, ApplicabilityFilter> pair : myApplicabilityFilters) {
      if (featureId.startsWith(pair.getFirst())) {
        filters.add(pair.getSecond());
      }
    }
    return filters.toArray(new ApplicabilityFilter[filters.size()]);
  }

  @Override
  public String toString() {
    return super.toString() + "; myAdditionalFeaturesLoaded="+myAdditionalFeaturesLoaded;
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
