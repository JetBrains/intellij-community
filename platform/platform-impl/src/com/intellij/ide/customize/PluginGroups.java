// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize;

import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class PluginGroups {
  public static final String CORE = "Core";
  private final Logger log = Logger.getInstance(PluginGroups.class);
  private static final int MAX_DESCR_LENGTH = 55;

  private final List<Group> myTree = new ArrayList<>();
  private final Map<PluginId, PluginGroupDescription> myFeaturedPlugins = new LinkedHashMap<>();

  private final Map<String, List<IdSet>> myGroups = new LinkedHashMap<>();
  private final Map<String, @Nls String> myDescriptions = new LinkedHashMap<>();

  private final Map<PluginId, PluginNode> myPluginsFromRepository = new HashMap<>();
  private final Set<PluginId> myDisabledPluginIds = new HashSet<>(DisabledPluginsState.loadDisabledPlugins());
  private final Map<PluginId, IdeaPluginDescriptorImpl> myEnabledPlugins = PluginDescriptorLoader.loadDescriptorsForDeprecatedWizard()
    .enabledPluginsById;

  private boolean myInitialized;
  private Runnable myLoadingCallback;

  public PluginGroups() {
    SwingWorker<List<PluginNode>, Object> worker = new SwingWorker<>() {
      @Override
      protected @NotNull List<PluginNode> doInBackground() {
        try {
          log.info("doInBackground 1");
          List<PluginNode> featuredPlugins = MarketplaceRequests.loadLastCompatiblePluginDescriptors(myFeaturedPlugins.keySet(), null, true);

          Set<PluginId> dependsIds = featuredPlugins.stream()
            .map(PluginNode::getDependencies)
            .flatMap(Collection::stream)
            .filter(dep -> !dep.isOptional())
            .map(IdeaPluginDependency::getPluginId)
            .collect(Collectors.toUnmodifiableSet());

          log.info("doInBackground 2");

          ArrayList<PluginNode> result = new ArrayList<>(featuredPlugins);
          result.addAll(MarketplaceRequests.loadLastCompatiblePluginDescriptors(dependsIds, null, true));

          log.info("doInBackground 3");
          return result;
        }
        catch (Throwable e) {
          log.warn(e);
          return List.of();
        }
      }

      @Override
      protected void done() {
        try {
          for (PluginNode node : get()) {
            myPluginsFromRepository.put(node.getPluginId(), node);
          }
          if (myLoadingCallback != null) myLoadingCallback.run();
        }
        catch (InterruptedException | ExecutionException e) {
          log.warn(e);
          if (myLoadingCallback != null) myLoadingCallback.run();
        }
      }
    };

    myTree.addAll(getInitialGroups());

    log.info("PluginGroups 1");

    Map<String, String> tempFeaturedPlugins = new LinkedHashMap<>();
    initGroups(myTree, tempFeaturedPlugins);
    // TODO:
    //  1) remove initGroups, initFeaturedPlugins usages;
    //  2) replace with for (PluginGroupDescription description : getInitialFeaturedPlugins());
    //  3) remove parsePluginId, parseString, add*Plugin usages;
    //  4) regenerate PluginGroupDescription#toString().
    for (Entry<String, String> entry : tempFeaturedPlugins.entrySet()) {
      @SuppressWarnings("HardCodedStringLiteral") String[] strings = parseString(entry.getValue());
      PluginGroupDescription description = PluginGroupDescription.create(/* idString = */ strings[2],
        /* name = */ entry.getKey(),
        /* category = */     strings[0],
        /* description = */     strings[1]);
      myFeaturedPlugins.put(description.getPluginId(), description);
    }

    log.info("PluginGroups 2");

    worker.execute();

    log.info("PluginGroups 3");
  }

  public void setLoadingCallback(Runnable loadingCallback) {
    myLoadingCallback = loadingCallback;
    if (!myPluginsFromRepository.isEmpty()) {
      myLoadingCallback.run();
    }
  }

  /**
   * @deprecated Please migrate to {@link #getInitialGroups()} and {@link #getInitialFeaturedPlugins()}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated(since = "2020.2", forRemoval = true)
  protected void initGroups(@NotNull List<? super Group> groups, @NotNull Map<String, String> featuredPlugins) {
    initFeaturedPlugins(featuredPlugins);
  }

  protected @NotNull List<Group> getInitialGroups() {
    return List.of();
  }

  /**
   * @deprecated Please migrate to {@link #getInitialFeaturedPlugins()}
   */
  @Deprecated(since = "2020.2", forRemoval = true)
  protected void initFeaturedPlugins(@NotNull Map<String, String> featuredPlugins) {
    for (PluginGroupDescription description : getInitialFeaturedPlugins()) {
      featuredPlugins.put(description.getName(), description.toString());
    }
  }

  protected @NotNull List<PluginGroupDescription> getInitialFeaturedPlugins() {
    return List.of();
  }

  /**
   * @deprecated For migration purpose only.
   */
  @Deprecated(since = "2020.2", forRemoval = true)
  private static @NotNull String @NotNull [] parseString(@NotNull @Nls String string) {
    return string.split(":", 3);
  }

  private void initIfNeeded() {
    if (myInitialized) return;
    myInitialized = true;
    for (Group g : myTree) {
      final String group = g.getId();
      if (CORE.equals(group)) continue;

      List<IdSet> idSets = new ArrayList<>();
      for (String idDescription : g.getPluginIdDescription()) {
        int i = idDescription.indexOf(":");
        IdSet idSet = createIdSet(i > 0 ? idDescription.substring(0, i) /* NON-NLS */ : null,
                                  i > 0 ? idDescription.substring(i + 1) : idDescription);
        if (idSet != null) {
          idSets.add(idSet);
        }
      }
      myGroups.put(group, idSets);

      @Nls StringBuilder description = new StringBuilder();
      StringUtil.join(idSets,
                      IdSet::getTitle,
                      ", ",
                      description);

      if (description.length() > MAX_DESCR_LENGTH) {
        int lastWord = description.lastIndexOf(",", MAX_DESCR_LENGTH);
        description.delete(lastWord, description.length()).append("...");
      }
      String groupDescription = g.getDescription();
      if (groupDescription != null) {
        description = new StringBuilder(groupDescription);
      }
      description.insert(0, "<html><body><center><i>");
      myDescriptions.put(group, description.toString());
    }
  }

  @NotNull
  public List<Group> getTree() {
    initIfNeeded();
    return myTree;
  }

  public @NotNull Map<PluginId, PluginGroupDescription> getFeaturedPluginDescriptions() {
    return Collections.unmodifiableMap(myFeaturedPlugins);
  }

  public @Nls String getDescription(String group) {
    initIfNeeded();
    return myDescriptions.get(group);
  }

  public List<IdSet> getSets(String group) {
    initIfNeeded();
    return myGroups.get(group);
  }

  public final @Nullable IdeaPluginDescriptor findPlugin(@NotNull PluginId pluginId) {
    return myEnabledPlugins.get(pluginId);
  }

  public final boolean isIdSetAllEnabled(@NotNull IdSet set) {
    for (PluginId id : set.getPluginIds()) {
      if (!isPluginEnabled(id)) {
        return false;
      }
    }
    return true;
  }

  public final void setIdSetEnabled(@NotNull IdSet set, boolean enabled) {
    for (PluginId id : set.getPluginIds()) {
      setPluginEnabledWithDependencies(id, enabled);
    }
  }

  public final @NotNull Set<PluginId> getDisabledPluginIds() {
    return Collections.unmodifiableSet(myDisabledPluginIds);
  }

  public final @NotNull Collection<PluginNode> getPluginsFromRepository() {
    return Collections.unmodifiableCollection(myPluginsFromRepository.values());
  }

  public final boolean isPluginEnabled(@NotNull PluginId pluginId) {
    initIfNeeded();
    return !myDisabledPluginIds.contains(pluginId);
  }

  private @NotNull Set<PluginId> getAllPluginIds(@NotNull Set<PluginId> pluginIds) {
    HashSet<PluginId> result = new HashSet<>(pluginIds);
    for (PluginId pluginId : pluginIds) {
      for (List<IdSet> sets : myGroups.values()) {
        for (IdSet set : sets) {
          Set<PluginId> ids = set.getPluginIds();
          if (ids.contains(pluginId)) {
            result.addAll(ids);
          }
        }
      }
    }
    return Collections.unmodifiableSet(result);
  }

  public void setPluginEnabledWithDependencies(@NotNull PluginId pluginId, boolean enabled) {
    initIfNeeded();

    Set<PluginId> accumulator = new HashSet<>();
    if (enabled) {
      collectIdsToEnable(pluginId, accumulator);
      myDisabledPluginIds.removeAll(getAllPluginIds(accumulator));
    }
    else {
      collectIdsToDisable(pluginId, accumulator);
      myDisabledPluginIds.addAll(getAllPluginIds(accumulator));
    }
  }

  private void collectIdsToEnable(@NotNull PluginId pluginId,
                                  @NotNull Set<PluginId> accumulator) {
    accumulator.add(pluginId);

    IdeaPluginDescriptorImpl descriptor = myEnabledPlugins.get(pluginId);
    if (descriptor == null) {
      return;
    }

    for (PluginDependency dependency : descriptor.pluginDependencies) {
      if (!dependency.isOptional() && !dependency.getPluginId().equals(PluginManagerCore.CORE_ID)) {
        collectIdsToEnable(dependency.getPluginId(), accumulator);
      }
    }
  }

  private void collectIdsToDisable(@NotNull PluginId pluginId,
                                   @NotNull Set<PluginId> accumulator) {
    accumulator.add(pluginId);

    for (IdeaPluginDescriptorImpl descriptor : myEnabledPlugins.values()) {
      for (PluginDependency dependency : descriptor.pluginDependencies) {
        if (!dependency.isOptional() && dependency.getPluginId().equals(pluginId)) {
          collectIdsToDisable(descriptor.getPluginId(), accumulator);
        }
      }
    }
  }

  private @Nullable IdSet createIdSet(@Nls @Nullable String title,
                                      @NonNls @NotNull String ids) {
    Ref<IdeaPluginDescriptorImpl> firstDescriptor = Ref.create();
    Set<PluginId> pluginIds = Arrays.stream(ids.split(","))
      .distinct()
      .map(PluginId::getId)
      .filter(pluginId -> {
        IdeaPluginDescriptorImpl descriptor = myEnabledPlugins.get(pluginId);
        firstDescriptor.setIfNull(descriptor);
        return descriptor != null;
      }).collect(Collectors.toUnmodifiableSet());

    int size = pluginIds.size();
    if (title == null && size > 1) {
      throw new IllegalArgumentException("There is no common title for " + size + " ids: " + ids);
    }

    return size != 0 ?
           new IdSet(pluginIds, title != null ? title : firstDescriptor.get().getName()) :
           null;
  }

  public static final class Group {
    private final @NonNls String myId;
    private final @Nls String myName;
    private final Icon myIcon;
    private final @Nls String myDescription;
    private final List<String> myPluginIdDescription;

    public Group(@NonNls @NotNull String id,
                 @Nls @NotNull String name,
                 @Nullable Icon icon,
                 @Nullable @Nls String description,
                 @NonNls @NotNull List<String> pluginIdDescription) {
      myId = id;
      myName = name;
      myIcon = icon;
      myDescription = description;
      myPluginIdDescription = pluginIdDescription;
    }

    public @NonNls String getId() {
      return myId;
    }

    public @NotNull @Nls String getName() {
      return myName;
    }

    public @Nullable Icon getIcon() {
      return myIcon;
    }

    public @Nullable @Nls String getDescription() {
      return myDescription;
    }

    public @NotNull List<String> getPluginIdDescription() {
      return myPluginIdDescription;
    }
  }
}
