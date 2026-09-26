// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.Function;
import com.intellij.util.ResourceUtil;
import kotlin.Unit;
import kotlinx.coroutines.CoroutineScope;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.diagnostic.ControlFlowExceptionsKt.rethrowControlFlowException;

@ApiStatus.Internal
public final class ProductivityFeaturesRegistryImpl extends ProductivityFeaturesRegistry {
  private static final Logger LOG = Logger.getInstance(ProductivityFeaturesRegistryImpl.class);

  private final Map<String, FeatureDescriptor> myFeatures = new HashMap<>();
  private final List<FeatureUsageEvent.Action> myActionEvents = new ArrayList<>();
  private final List<FeatureUsageEvent.Intention> myIntentionEvents = new ArrayList<>();
  private final Map<String, GroupDescriptor> myGroups = new HashMap<>();
  private final List<ApplicabilityFiltersData> myApplicabilityFilters = new ArrayList<>();
  /** The IDs of the features that each {@link ProductivityFeaturesBean} contributed. */
  private final Map<ProductivityFeaturesBean, List<String>> myXmlFeatureIds = new HashMap<>();

  private boolean myLoaded;

  private static final @NonNls String TAG_GROUP = "group";
  private static final @NonNls String TAG_FEATURE = "feature";

  public ProductivityFeaturesRegistryImpl(@NotNull CoroutineScope coroutineScope) {
    ProductivityFeaturesBean.EP_NAME.addExtensionPointListener(coroutineScope, new ExtensionPointListener<>() {
      @Override
      public void extensionRemoved(@NotNull ProductivityFeaturesBean extension, @NotNull PluginDescriptor pluginDescriptor) {
        removeXmlFeatures(extension);
      }
    });
    ProductivityFeaturesProvider.EP_NAME.addExtensionPointListener(coroutineScope, new ExtensionPointListener<>() {
      @Override
      public void extensionRemoved(@NotNull ProductivityFeaturesProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        removeProvidedFeatures(extension);
      }
    });
  }

  private void loadIfNeeded() {
    if (myLoaded) {
      return;
    }

    myLoaded = true;
    ProductivityFeaturesBean.EP_NAME.processWithPluginDescriptor((bean, pluginDescriptor) -> {
      loadXml(bean, pluginDescriptor);
      return Unit.INSTANCE;
    });
    ProductivityFeaturesProvider.EP_NAME.processWithPluginDescriptor((provider, pluginDescriptor) -> {
      loadProvider(provider, pluginDescriptor);
      return Unit.INSTANCE;
    });
  }

  private void loadXml(@NotNull ProductivityFeaturesBean bean, @NotNull PluginDescriptor pluginDescriptor) {
    String path = Strings.trimStart(bean.file, "/");
    try {
      List<String> featureIds = readFromXml(path, getClassLoader(pluginDescriptor), null);
      if (featureIds == null) {
        LOG.error(new PluginException(path + " not found", pluginDescriptor.getPluginId()));
      }
      else {
        myXmlFeatureIds.put(bean, featureIds);
      }
    }
    catch (Exception e) {
      rethrowControlFlowException(e);
      LOG.error(new PluginException("Error while reading " + path, e, pluginDescriptor.getPluginId()));
    }
  }

  private void loadProvider(@NotNull ProductivityFeaturesProvider provider, @NotNull PluginDescriptor pluginDescriptor) {
    for (String xmlUrl : provider.getXmlFilesUrls()) {
      try {
        readFromXml(Strings.trimStart(xmlUrl, "/"), getClassLoader(pluginDescriptor), provider);
      }
      catch (Exception e) {
        rethrowControlFlowException(e);
        LOG.error(new PluginException("Error while reading " + xmlUrl + " from " + provider, e, pluginDescriptor.getPluginId()));
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
  }

  private static @NotNull ClassLoader getClassLoader(@NotNull PluginDescriptor pluginDescriptor) {
    ClassLoader classLoader = pluginDescriptor.getPluginClassLoader();
    return classLoader == null ? ProductivityFeaturesRegistryImpl.class.getClassLoader() : classLoader;
  }

  /**
   * @return the IDs of the loaded features, or {@code null} when the resource does not exist
   */
  private @Nullable List<String> readFromXml(@NotNull String path,
                                             @NotNull ClassLoader classLoader,
                                             @Nullable ProductivityFeaturesProvider provider) throws JDOMException, IOException {
    byte[] data = ResourceUtil.getResourceAsBytes(path, classLoader, true);
    if (data == null) {
      return null;
    }

    List<String> featureIds = new ArrayList<>();
    Element root = JDOMUtil.load(data);
    for (Element groupElement : root.getChildren(TAG_GROUP)) {
      readGroup(groupElement, provider, featureIds);
    }
    return featureIds;
  }

  private void addUsageEvents(FeatureDescriptor featureDescriptor) {
    myActionEvents.addAll(featureDescriptor.getActionEvents());
    myIntentionEvents.addAll(featureDescriptor.getIntentionEvents());
  }

  private void readGroup(Element groupElement, @Nullable ProductivityFeaturesProvider provider, @NotNull List<String> featureIds) {
    GroupDescriptor groupDescriptor = new GroupDescriptor();
    groupDescriptor.readExternal(groupElement);
    String groupId = groupDescriptor.getId();
    myGroups.putIfAbsent(groupId, groupDescriptor);  // do not allow to override groups
    readFeatures(groupElement, groupDescriptor, provider, featureIds);
  }

  private void readFeatures(Element groupElement,
                            GroupDescriptor groupDescriptor,
                            @Nullable ProductivityFeaturesProvider provider,
                            @NotNull List<String> featureIds) {
    for (Element featureElement : groupElement.getChildren(TAG_FEATURE)) {
      FeatureDescriptor featureDescriptor = new FeatureDescriptor(groupDescriptor, provider, featureElement);
      addFeature(featureDescriptor);
      featureIds.add(featureDescriptor.getId());
    }
  }

  private void addFeature(@NotNull FeatureDescriptor descriptor) {
    final FeatureDescriptor existingDescriptor = myFeatures.get(descriptor.getId());
    if (existingDescriptor != null) {
      LOG.info("Feature with id '" + descriptor.getId() + "' is overridden by: " + descriptor);
      descriptor.copyStatistics(existingDescriptor);
    }
    myFeatures.put(descriptor.getId(), descriptor);
    addUsageEvents(descriptor);
  }

  private void removeXmlFeatures(@NotNull ProductivityFeaturesBean bean) {
    List<String> featureIds = myXmlFeatureIds.remove(bean);
    if (featureIds == null) {
      return;
    }
    removeFeatures(featureIds);

    LOG.info("Removed features loaded from " + bean.file + ": " + featureIds);
  }

  private void removeProvidedFeatures(@NotNull ProductivityFeaturesProvider provider) {
    Class<? extends ProductivityFeaturesProvider> providerClass = provider.getClass();
    Set<String> featureIdsToRemove = myFeatures.entrySet().stream()
      .filter(entry -> entry.getValue().getProvider() == providerClass)
      .map(entry -> entry.getKey())
      .collect(Collectors.toSet());
    removeFeatures(featureIdsToRemove);
    myApplicabilityFilters.removeIf(data -> data.provider == provider);

    LOG.info("Removed features provided by " + providerClass.getName() + ": " + featureIdsToRemove);
  }

  private void removeFeatures(@NotNull Collection<String> featureIds) {
    featureIds.forEach(myFeatures::remove);
    myActionEvents.removeIf(event -> featureIds.contains(event.featureId()));
    myIntentionEvents.removeIf(event -> featureIds.contains(event.featureId()));
  }

  private @Nullable <T extends FeatureUsageEvent> FeatureDescriptor findFeatureByEvent(List<? extends T> events,
                                                                                       Function<? super T, Boolean> eventChecker) {
    loadIfNeeded();
    return events
      .stream()
      .filter(e -> eventChecker.fun(e))
      .findFirst()
      .map(e -> getFeatureDescriptor(e.featureId()))
      .orElse(null);
  }

  @Override
  public @NotNull Set<String> getFeatureIds() {
    loadIfNeeded();
    return myFeatures.keySet();
  }

  @Override
  public FeatureDescriptor getFeatureDescriptor(@NotNull String id) {
    loadIfNeeded();
    return myFeatures.get(id);
  }

  @Override
  public GroupDescriptor getGroupDescriptor(@NotNull String id) {
    loadIfNeeded();
    return myGroups.get(id);
  }

  @Override
  public ApplicabilityFilter @NotNull [] getMatchingFilters(@NotNull String featureId) {
    loadIfNeeded();
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
  public @NonNls String toString() {
    return super.toString() + "; myLoaded=" + myLoaded;
  }

  private record ApplicabilityFiltersData(@NotNull ProductivityFeaturesProvider provider, ApplicabilityFilter @NotNull [] filters) {
  }

  @TestOnly
  public void prepareForTest() {
    myLoaded = false;
    myFeatures.clear();
    myActionEvents.clear();
    myIntentionEvents.clear();
    myGroups.clear();
    myApplicabilityFilters.clear();
    myXmlFeatureIds.clear();
  }
}
