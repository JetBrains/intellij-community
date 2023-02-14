// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.JDOMUtil;
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
import java.util.stream.Collectors;

public final class ProductivityFeaturesRegistryImpl extends ProductivityFeaturesRegistry {
  private static final Logger LOG = Logger.getInstance(ProductivityFeaturesRegistryImpl.class);

  private final Map<String, FeatureDescriptor> myFeatures = new HashMap<>();
  private final List<FeatureUsageEvent.Action> myActionEvents = new ArrayList<>();
  private final List<FeatureUsageEvent.Intention> myIntentionEvents = new ArrayList<>();
  private final Map<String, GroupDescriptor> myGroups = new HashMap<>();
  private final List<ApplicabilityFiltersData> myApplicabilityFilters = new ArrayList<>();

  private record ConfigurationSource(@NotNull String path, boolean isRequired) {
  }

  private final List<ConfigurationSource> myFeatureConfigurationSources = List.of(
    new ConfigurationSource("PlatformProductivityFeatures.xml", true),  // common features that exist in all IDEs
    new ConfigurationSource("ProductivityFeaturesRegistry.xml", true),  // product specific features (IDEA, PyCharm, etc...)
    new ConfigurationSource("IdeSpecificFeatures.xml", false)  // IDE specific features (IDEA Ultimate, PyCharm Professional, etc...)
  );

  private boolean myAdditionalFeaturesLoaded;

  @NonNls private static final String TAG_GROUP = "group";
  @NonNls private static final String TAG_FEATURE = "feature";

  public ProductivityFeaturesRegistryImpl() {
    reloadFromXml();
    ProductivityFeaturesProvider.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionRemoved(@NotNull ProductivityFeaturesProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        removeProvidedFeatures(extension);
      }
    });
  }

  private void reloadFromXml() {
    for (ConfigurationSource source : myFeatureConfigurationSources) {
      boolean found;
      try {
        found = readFromXml(source.path);
      }
      catch (Throwable e) {
        LOG.error(e);
        found = false;
      }
      if (source.isRequired && !found && !ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.error(source.path + " not found");
      }
    }
  }

  private boolean readFromXml(@NotNull @NonNls String path) throws JDOMException, IOException {
    return readFromXml(path, ProductivityFeaturesRegistryImpl.class.getClassLoader(), null);
  }

  private boolean readFromXml(@NotNull String path, @NotNull ClassLoader classLoader, @Nullable ProductivityFeaturesProvider provider)
    throws JDOMException, IOException {
    byte[] data = ResourceUtil.getResourceAsBytes(path, classLoader, true);
    if (data == null) {
      return false;
    }

    Element root = JDOMUtil.load(data);
    for (Element groupElement : root.getChildren(TAG_GROUP)) {
      readGroup(groupElement, provider);
    }
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
          readFromXml(Strings.trimStart(xmlUrl, "/"), pluginDescriptor.getClassLoader(), provider);
        }
        catch (Exception e) {
          LOG.error(new PluginException("Error while reading " + xmlUrl + " from " + provider + ": " + e.getMessage(),
                                        pluginDescriptor.getPluginId()));
        }
      }

      final GroupDescriptor[] groupDescriptors = provider.getGroupDescriptors();
      if (groupDescriptors != null) {
        for (GroupDescriptor groupDescriptor : groupDescriptors) {
          // do not allow to override groups
          myGroups.putIfAbsent(groupDescriptor.getId(), groupDescriptor);
        }
      }
      final FeatureDescriptor[] featureDescriptors = provider.getFeatureDescriptors();
      if (featureDescriptors != null) {
        for (FeatureDescriptor featureDescriptor : featureDescriptors) {
          addFeature(featureDescriptor);
        }
      }
      final ApplicabilityFilter[] applicabilityFilters = provider.getApplicabilityFilters();
      if (applicabilityFilters != null) {
        myApplicabilityFilters.add(new ApplicabilityFiltersData(provider, applicabilityFilters));
      }
    });
  }

  private void addUsageEvents(FeatureDescriptor featureDescriptor) {
    myActionEvents.addAll(featureDescriptor.getActionEvents());
    myIntentionEvents.addAll(featureDescriptor.getIntentionEvents());
  }

  private void readGroup(Element groupElement, ProductivityFeaturesProvider provider) {
    GroupDescriptor groupDescriptor = new GroupDescriptor();
    groupDescriptor.readExternal(groupElement);
    String groupId = groupDescriptor.getId();
    myGroups.putIfAbsent(groupId, groupDescriptor);  // do not allow to override groups
    readFeatures(groupElement, groupDescriptor, provider);
  }

  private void readFeatures(Element groupElement, GroupDescriptor groupDescriptor, ProductivityFeaturesProvider provider) {
    for (Element featureElement : groupElement.getChildren(TAG_FEATURE)) {
      FeatureDescriptor featureDescriptor = new FeatureDescriptor(groupDescriptor, provider, featureElement);
      addFeature(featureDescriptor);
    }
  }

  private void addFeature(@NotNull FeatureDescriptor descriptor) {
    // Allow to override features for now, but maybe it should be restricted
    final FeatureDescriptor existingDescriptor = myFeatures.get(descriptor.getId());
    if (existingDescriptor != null) {
      LOG.warn("Feature with id '" + descriptor.getId() + "' is overridden by: " + descriptor);
      descriptor.copyStatistics(existingDescriptor);
    }
    myFeatures.put(descriptor.getId(), descriptor);
    addUsageEvents(descriptor);
  }

  private void removeProvidedFeatures(@NotNull ProductivityFeaturesProvider provider) {
    Class<? extends ProductivityFeaturesProvider> providerClass = provider.getClass();
    Set<String> featureIdsToRemove = myFeatures.entrySet().stream()
      .filter(entry -> entry.getValue().getProvider() == providerClass)
      .map(entry -> entry.getKey())
      .collect(Collectors.toSet());
    featureIdsToRemove.forEach(id -> myFeatures.remove(id));
    myActionEvents.removeIf(event -> featureIdsToRemove.contains(event.featureId()));
    myIntentionEvents.removeIf(event -> featureIdsToRemove.contains(event.featureId()));
    myApplicabilityFilters.removeIf(data -> data.provider == provider);

    LOG.info("Removed features provided by " + providerClass.getName() + ": " + featureIdsToRemove);
  }

  private @Nullable <T extends FeatureUsageEvent> FeatureDescriptor findFeatureByEvent(List<? extends T> events,
                                                                                       Function<? super T, Boolean> eventChecker) {
    lazyLoadFromPluginsFeaturesProviders();
    return events
      .stream()
      .filter(e -> eventChecker.fun(e))
      .findFirst()
      .map(e -> getFeatureDescriptor(e.featureId()))
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
    FeatureDescriptor descriptor = myFeatures.get(featureId);
    if (descriptor != null) {
      Class<? extends ProductivityFeaturesProvider> providerClass = descriptor.getProvider();
      return myApplicabilityFilters.stream()
        .filter(it -> it.provider.getClass() == providerClass)
        .findFirst()
        .map(it -> it.filters)
        .orElse(new ApplicabilityFilter[0]);
    }
    return new ApplicabilityFilter[0];
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

  private record ApplicabilityFiltersData(@NotNull ProductivityFeaturesProvider provider, ApplicabilityFilter @NotNull [] filters) {
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
