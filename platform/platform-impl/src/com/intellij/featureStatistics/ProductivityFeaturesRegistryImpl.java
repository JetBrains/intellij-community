/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class ProductivityFeaturesRegistryImpl extends ProductivityFeaturesRegistry {
  private static final Logger LOG = Logger.getInstance("#com.intellij.featureStatistics.ProductivityFeaturesRegistry");
  private final Map<String, FeatureDescriptor> myFeatures = new HashMap<String, FeatureDescriptor>();
  private final Map<String, GroupDescriptor> myGroups = new HashMap<String, GroupDescriptor>();
  private final List<Pair<String, ApplicabilityFilter>> myApplicabilityFilters = new ArrayList<Pair<String,ApplicabilityFilter>>();

  private boolean myLoadAdditionFeatures = false;

  public static final @NonNls String WELCOME = "features.welcome";

  private static final @NonNls String TAG_FILTER = "filter";
  private static final @NonNls String TAG_GROUP = "group";
  private static final @NonNls String TAG_FEATURE = "feature";
  private static final @NonNls String TODO_HTML_MARKER = "todo.html";
  @NonNls private static final String CLASS_ATTR = "class";
  @NonNls private static final String PREFIX_ATTR = "prefix";

  public ProductivityFeaturesRegistryImpl() {
    try {
      readFromXml("file:///ProductivityFeaturesRegistry.xml");
    }
    catch (Exception e) {
      LOG.error(e);
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
    loadFeaturesFromProviders(ApplicationManager.getApplication().getComponents(ProductivityFeaturesProvider.class));
    loadFeaturesFromProviders(Extensions.getExtensions(ProductivityFeaturesProvider.EP_NAME));
    myLoadAdditionFeatures = true;
  }

  private void loadFeaturesFromProviders(ProductivityFeaturesProvider[] providers) {
    for (ProductivityFeaturesProvider provider : providers) {
      final GroupDescriptor[] groupDescriptors = provider.getGroupDescriptors();
      for (int j = 0; groupDescriptors != null && j < groupDescriptors.length; j++) {
        GroupDescriptor groupDescriptor = groupDescriptors[j];
        myGroups.put(groupDescriptor.getId(), groupDescriptor);
      }
      final FeatureDescriptor[] featureDescriptors = provider.getFeatureDescriptors();
      for (int j = 0; featureDescriptors != null && j < featureDescriptors.length; j++) {
        FeatureDescriptor featureDescriptor = featureDescriptors[j];
        final FeatureDescriptor featureLoadedStatistics = myFeatures.get(featureDescriptor.getId());
        if (featureLoadedStatistics != null) {
          featureDescriptor.copyStatistics(featureLoadedStatistics);
        }
        myFeatures.put(featureDescriptor.getId(), featureDescriptor);
      }
      final ApplicabilityFilter[] applicabilityFilters = provider.getApplicabilityFilters();
      for (int j = 0; applicabilityFilters != null && j < applicabilityFilters.length; j++) {
        ApplicabilityFilter applicabilityFilter = applicabilityFilters[j];
        myApplicabilityFilters.add(new Pair<String, ApplicabilityFilter>(applicabilityFilter.getPrefix(), applicabilityFilter));
      }
    }
  }

  private void readFilters(Element element) {
    List filters = element.getChildren(TAG_FILTER);
    for (int i = 0; i < filters.size(); i++) {
      Element filterElement = (Element)filters.get(i);
      String className = filterElement.getAttributeValue(CLASS_ATTR);
      try {
        Class klass = Class.forName(className);
        if (!ApplicabilityFilter.class.isAssignableFrom(klass)) {
          LOG.error("filter class must implement com.intellij.featureSatistics.ApplicabilityFilter");
          continue;
        }

        ApplicabilityFilter filter = (ApplicabilityFilter)klass.newInstance();
        myApplicabilityFilters.add(new Pair<String, ApplicabilityFilter>(filterElement.getAttributeValue(PREFIX_ATTR), filter));
      }
      catch (Exception e) {
        LOG.error("Cannot instantiate filter " + className, e);
      }
    }
  }

  private void readGroups(Element element) {
    List groups = element.getChildren(TAG_GROUP);
    for (int i = 0; i < groups.size(); i++) {
      Element groupElement = (Element)groups.get(i);
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
    for (int i = 0; i < features.size(); i++) {
      Element featureElement = (Element)features.get(i);
      FeatureDescriptor featureDescriptor = new FeatureDescriptor(groupDescriptor);
      featureDescriptor.readExternal(featureElement);
      if (!TODO_HTML_MARKER.equals(featureDescriptor.getTipFileName())) {
        myFeatures.put(featureDescriptor.getId(), featureDescriptor);
      }
    }
  }

  public Set<String> getFeatureIds() {
    if (!myLoadAdditionFeatures){
      lazyLoadFromPluginsFeaturesProviders();
    }
    return myFeatures.keySet();
  }

  public FeatureDescriptor getFeatureDescriptor(String id) {
    if (WELCOME.equals(id)) {
      FeatureDescriptor descriptor = new FeatureDescriptor(WELCOME, "AdaptiveWelcome.html", FeatureStatisticsBundle.message("feature.statistics.welcome.tip.name"));
      return descriptor;
    }
    if (!myLoadAdditionFeatures){
      lazyLoadFromPluginsFeaturesProviders();
    }
    return myFeatures.get(id);
  }

  public FeatureDescriptor getFeatureDescriptorEx(String id) {
    if (WELCOME.equals(id)) {
      FeatureDescriptor descriptor = new FeatureDescriptor(WELCOME, "AdaptiveWelcome.html", FeatureStatisticsBundle.message("feature.statistics.welcome.tip.name"));
      return descriptor;
    }
    return myFeatures.get(id);
  }

  public GroupDescriptor getGroupDescriptor(String id) {
    return myGroups.get(id);
  }

  public ApplicabilityFilter[] getMatchingFilters(String featureId) {
    if (!myLoadAdditionFeatures){
      lazyLoadFromPluginsFeaturesProviders();
    }
    List<ApplicabilityFilter> filters = new ArrayList<ApplicabilityFilter>();
    for (int i = 0; i < myApplicabilityFilters.size(); i++) {
      Pair<String, ApplicabilityFilter> pair = myApplicabilityFilters.get(i);
      if (featureId.startsWith(pair.getFirst())) {
        filters.add(pair.getSecond());
      }
    }
    return filters.toArray(new ApplicabilityFilter[filters.size()]);
  }

  public void addFeatureStatistics(final FeatureDescriptor descriptor) {
    myFeatures.put(descriptor.getId(), descriptor);
  }
}
