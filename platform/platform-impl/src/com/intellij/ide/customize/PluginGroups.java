// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.cloudConfig.CloudConfigProvider;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import icons.PlatformImplIcons;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PluginGroups {
  public static final String CORE = "Core";
  private static final int MAX_DESCR_LENGTH = 55;

  private final List<Group> myTree = new ArrayList<>();
  private final Map<PluginId, PluginGroupDescription> myFeaturedPlugins = new LinkedHashMap<>();

  private final Map<String, List<IdSet>> myGroups = new LinkedHashMap<>();
  private final Map<String, @Nls String> myDescriptions = new LinkedHashMap<>();
  private final List<PluginNode> myPluginsFromRepository = new ArrayList<>();
  private final Collection<PluginId> myDisabledPluginIds = new HashSet<>();
  private final List<IdeaPluginDescriptorImpl> myAllPlugins;
  private boolean myInitialized;
  private Runnable myLoadingCallback;

  public PluginGroups() {
    myAllPlugins = PluginDescriptorLoader.loadUncachedDescriptors(PluginManagerCore.isUnitTestMode, PluginManagerCore.isRunningFromSources());
    SwingWorker<List<PluginNode>, Object> worker = new SwingWorker<>() {
      @Override
      protected @NotNull List<PluginNode> doInBackground() {
        try {
          MarketplaceRequests requests = MarketplaceRequests.getInstance();
          List<PluginNode> featuredPlugins = requests.loadLastCompatiblePluginDescriptors(myFeaturedPlugins.keySet());

          Set<PluginId> dependsIds = featuredPlugins.stream()
            .map(PluginNode::getDependencies)
            .flatMap(Collection::stream)
            .filter(dep -> !dep.isOptional())
            .map(IdeaPluginDependency::getPluginId)
            .collect(Collectors.toUnmodifiableSet());

          ArrayList<PluginNode> result = new ArrayList<>(featuredPlugins);
          result.addAll(requests.loadLastCompatiblePluginDescriptors(dependsIds));
          return result;
        }
        catch (Exception e) {
          //OK, it's offline
          return List.of();
        }
      }

      @Override
      protected void done() {
        try {
          myPluginsFromRepository.addAll(get());
          if (myLoadingCallback != null) myLoadingCallback.run();
        }
        catch (InterruptedException | ExecutionException e) {
          if (myLoadingCallback != null) myLoadingCallback.run();
        }
      }
    };

    myTree.addAll(getInitialGroups());

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

    worker.execute();
    myDisabledPluginIds.addAll(DisabledPluginsState.loadDisabledPlugins());
    initCloudPlugins(CloudConfigProvider.getProvider());
  }

  public void setLoadingCallback(Runnable loadingCallback) {
    myLoadingCallback = loadingCallback;
    if (!myPluginsFromRepository.isEmpty()) {
      myLoadingCallback.run();
    }
  }

  private void initCloudPlugins(@Nullable CloudConfigProvider provider) {
    for (PluginId pluginId : provider != null ? provider.getInstalledPlugins() : Set.<PluginId>of()) {
      myFeaturedPlugins.computeIfPresent(pluginId,
                                         PluginGroupDescription.CloudDelegate::new);
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
    return List.of(
      new Group(CORE,
                IdeBundle.message("label.plugin.group.name.core"),
                null,
                null,
                List.of(
                  "com.intellij.copyright",
                  "com.intellij.java-i18n",
                  "org.intellij.intelliLang",
                  "com.intellij.properties",
                  "Refactor-X",//?
                  "Type Migration",
                  "ZKM"
                )),
      new Group("Java Frameworks",
                IdeBundle.message("label.plugin.group.name.java.frameworks"),
                PlatformImplIcons.JavaFrameworks,
                null,
                List.of(
                  "Spring:com.intellij.spring.batch," +
                  "com.intellij.spring.data," +
                  "com.intellij.spring.integration," +
                  "com.intellij.spring.osgi," +
                  "com.intellij.spring.security," +
                  "com.intellij.spring," +
                  "com.intellij.spring.webflow," +
                  "com.intellij.spring.ws,com.intellij.aop",

                  "Java EE:com.intellij.javaee.batch," +
                  "com.intellij.beanValidation," +
                  "com.intellij.cdi," +
                  "com.intellij.javaee," +
                  "com.intellij.jsf," +
                  "com.intellij.javaee.extensions," +
                  "com.jetbrains.restWebServices," +
                  "Web Services (JAX-WS)," +
                  "com.intellij.javaee.webSocket," +
                  "com.intellij.jsp",

                  "com.intellij.hibernate",
                  "com.intellij.reactivestreams",
                  "com.intellij.frameworks.java.sql",
                  // preview ends

                  "org.intellij.grails",

                  "com.intellij.micronaut",
                  "com.intellij.quarkus",
                  "com.intellij.helidon",

                  "com.intellij.guice",

                  "com.intellij.freemarker",
                  "com.intellij.velocity",
                  "com.intellij.aspectj"
                )),
      new Group("Build Tools",
                IdeBundle.message("label.plugin.group.name.build.tools"),
                PlatformImplIcons.BuildTools,
                null,
                List.of(
                  "AntSupport",
                  "Maven:org.jetbrains.idea.maven,org.jetbrains.idea.maven.ext",
                  "org.jetbrains.plugins.gradle"
                )),
      new Group("JavaScript Development",
                IdeBundle.message("label.plugin.group.name.javascript.development"),
                PlatformImplIcons.WebDevelopment,
                IdeBundle.message("label.plugin.group.description.javascript.development"),
                List.of(
                  "HTML:HtmlTools,W3Validators",
                  "JavaScript and TypeScript:JavaScript,JavaScriptDebugger,JSIntentionPowerPack",
                  "Node.js:NodeJS",

                  "com.intellij.css",
                  "org.jetbrains.plugins.less",
                  "org.jetbrains.plugins.sass",

                  "org.jetbrains.plugins.stylus",
                  "org.jetbrains.plugins.haml",
                  "AngularJS",

                  "org.coffeescript",
                  "com.jetbrains.restClient",

                  "com.intellij.swagger"
                )),
      Group.vcs(),
      new Group("Test Tools",
                IdeBundle.message("label.plugin.group.name.test.tools"),
                PlatformImplIcons.TestTools,
                null,
                List.of(
                  "JUnit",
                  "TestNG-J",
                  "cucumber-java",
                  "cucumber",
                  "Coverage:Coverage,Emma"
                )),
      new Group("Application Servers",
                IdeBundle.message("label.plugin.group.name.application.servers"),
                PlatformImplIcons.ApplicationServers,
                null,
                List.of(
                  "com.intellij.javaee.view",
                  "Geronimo",
                  "GlassFish",
                  "JBoss",
                  "Jetty",
                  "Tomcat",
                  "Weblogic",
                  "WebSphere",
                  "JSR45Plugin"
                )),
      //myTree.put("Groovy", List.of("org.intellij.grails"));
      //TODO Scala -> Play 2.x (Play 2.0 Support)
      new Group("Swing",
                IdeBundle.message("label.plugin.group.name.swing"),
                PlatformImplIcons.Swing,
                null,
                List.of("com.intellij.uiDesigner") /* TODO JavaFX? */),
      new Group("Android",
                IdeBundle.message("label.plugin.group.name.android"),
                PlatformImplIcons.Android,
                null,
                List.of(
                  "org.jetbrains.android",
                  "com.intellij.android-designer")),
      new Group("Database Tools",
                IdeBundle.message("label.plugin.group.name.database.tools"),
                PlatformImplIcons.DatabaseTools,
                null,
                List.of("com.intellij.database")),
      new Group("Other Tools",
                IdeBundle.message("label.plugin.group.name.other.tools"),
                PlatformImplIcons.OtherTools,
                null,
                List.of(
                  "ByteCodeViewer",
                  "com.intellij.dsm",
                  "org.jetbrains.idea.eclipse",
                  "org.jetbrains.debugger.streams",
                  "Remote Access:com.jetbrains.plugins.webDeployment,org.jetbrains.plugins.remote-run",
                  "Task Management:com.intellij.tasks,com.intellij.tasks.timeTracking",
                  "org.jetbrains.plugins.terminal",
                  "org.jetbrains.plugins.emojipicker",
                  "com.intellij.diagram",
                  "org.jetbrains.plugins.yaml",
                  "XSLT and XPath:XPathView,XSLT-Debugger"
                )),
      new Group("Plugin Development",
                IdeBundle.message("label.plugin.group.name.plugin.development"),
                PlatformImplIcons.PluginDevelopment,
                null,
                List.of("DevKit"))
    );
  }

  /**
   * @deprecated Please migrate to {@link #getInitialFeaturedPlugins()}
   */
  @Deprecated(since = "2020.2", forRemoval = true)
  protected void initFeaturedPlugins(@NotNull Map<String, String> featuredPlugins) {
    for (PluginGroupDescription description : getInitialFeaturedPlugins()) {
      addPlugin(featuredPlugins, description);
    }
  }

  protected @NotNull List<? extends PluginGroupDescription> getInitialFeaturedPlugins() {
    return List.of(
      PluginGroupDescription.scala(),
      PluginGroupDescription.liveEdit(),
      PluginGroupDescription.vim(),
      PluginGroupDescription.create("atlassian-idea-plugin",
                                    "Atlassian Connector",
                                    "Tools Integration",
                                    "Integration for Atlassian JIRA, Bamboo, Crucible, FishEye"),
      PluginGroupDescription.featuresTrainer()
    );
  }

  /**
   * @deprecated Please migrate to {@link PluginGroupDescription}.
   */
  @Deprecated(since = "2020.2", forRemoval = true)
  public static @NotNull @NonNls String parsePluginId(@NotNull @Nls String string) {
    return parseString(string)[2];
  }

  /**
   * @deprecated Please migrate to {@link PluginGroupDescription#vim()}.
   */
  @Deprecated(since = "2020.2", forRemoval = true)
  public static void addVimPlugin(@NotNull Map<String, String> featuredPlugins) {
    addPlugin(featuredPlugins, PluginGroupDescription.vim());
  }

  /**
   * @deprecated Please migrate to {@link PluginGroupDescription#aws()}.
   */
  @Deprecated(since = "2020.2", forRemoval = true)
  public static void addAwsPlugin(@NotNull Map<String, String> featuredPlugins) {
    addPlugin(featuredPlugins, PluginGroupDescription.aws());
  }

  /**
   * @deprecated Please migrate to {@link PluginGroupDescription#teamCity()}.
   */
  @Deprecated(since = "2020.2", forRemoval = true)
  public static void addTeamCityPlugin(@NotNull Map<String, String> featuredPlugins) {
    addPlugin(featuredPlugins, PluginGroupDescription.teamCity());
  }

  /**
   * @deprecated Please migrate to {@link #getInitialFeaturedPlugins()}.
   */
  @Deprecated(since = "2020.2", forRemoval = true)
  private static void addPlugin(@NotNull Map<String, String> featuredPlugins,
                                @NotNull PluginGroupDescription description) {
    featuredPlugins.put(description.getName(), description.toString());
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

  /**
   * @deprecated Please use {@link #getFeaturedPluginDescriptions()} instead.
   */
  @Deprecated(forRemoval = true, since = "2020.2")
  public Map<@NlsSafe String, @Nls String> getFeaturedPlugins() {
    Map<String, String> featuredPlugins = new LinkedHashMap<>();
    for (PluginGroupDescription description : myFeaturedPlugins.values()) {
      addPlugin(featuredPlugins, description);
    }
    return featuredPlugins;
  }

  public @NotNull Map<PluginId, ? extends PluginGroupDescription> getFeaturedPluginDescriptions() {
    return myFeaturedPlugins;
  }

  public @Nls String getDescription(String group) {
    initIfNeeded();
    return myDescriptions.get(group);
  }

  public List<IdSet> getSets(String group) {
    initIfNeeded();
    return myGroups.get(group);
  }

  public final @Nullable IdeaPluginDescriptor findPlugin(@NotNull PluginId id) {
    for (IdeaPluginDescriptor pluginDescriptor : myAllPlugins) {
      if (id.equals(pluginDescriptor.getPluginId())) {
        return pluginDescriptor;
      }
    }
    return null;
  }

  public boolean isIdSetAllEnabled(@NotNull IdSet set) {
    for (PluginId id : set.getPluginIds()) {
      if (!isPluginEnabled(id)) {
        return false;
      }
    }
    return true;
  }

  public void setIdSetEnabled(@NotNull IdSet set, boolean enabled) {
    for (PluginId id : set.getPluginIds()) {
      setPluginEnabledWithDependencies(id, enabled);
    }
  }

  @NotNull
  public Collection<PluginId> getDisabledPluginIds() {
    return Collections.unmodifiableCollection(myDisabledPluginIds);
  }

  public List<PluginNode> getPluginsFromRepository() {
    return myPluginsFromRepository;
  }

  public boolean isPluginEnabled(@NotNull PluginId pluginId) {
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

    Function<PluginId, List<IdeaPluginDescriptorImpl>> pluginsById =
      enabled ?
      new Function<>() {
        private final Map<PluginId, IdeaPluginDescriptorImpl> myPluginById = myAllPlugins.stream()
          .collect(Collectors.toUnmodifiableMap(IdeaPluginDescriptorImpl::getPluginId, Function.identity()));

        @Override
        public @NotNull List<IdeaPluginDescriptorImpl> apply(PluginId pluginId) {
          IdeaPluginDescriptorImpl descriptor = myPluginById.get(pluginId);
          return descriptor != null ? List.of(descriptor) : List.of();
        }
      } : (__ -> myAllPlugins);

    BiPredicate<PluginId, PluginId> predicate =
      enabled ?
      (dependencyId, __) -> !PluginManagerCore.CORE_ID.equals(dependencyId) :
      Objects::equals;

    Set<PluginId> pluginIds = getAllPluginIds(collectInvolvedIds(pluginId, predicate, pluginsById));
    if (enabled) {
      myDisabledPluginIds.removeAll(pluginIds);
    }
    else {
      myDisabledPluginIds.addAll(pluginIds);
    }
  }

  private static @NotNull Set<PluginId> collectInvolvedIds(@NotNull PluginId pluginId,
                                                           @NotNull BiPredicate<@NotNull PluginId, @NotNull PluginId> predicate,
                                                           @NotNull Function<@NotNull PluginId, @NotNull List<IdeaPluginDescriptorImpl>> pluginsById) {
    Set<PluginId> pluginIds = new HashSet<>();
    pluginIds.add(pluginId);

    for (IdeaPluginDescriptorImpl plugin : pluginsById.apply(pluginId)) {
      for (PluginDependency dependency : plugin.getPluginDependencies()) {
        if (!dependency.isOptional() && predicate.test(dependency.getPluginId(), pluginId)) {
          pluginIds.addAll(collectInvolvedIds(dependency.getPluginId(), predicate, pluginsById));
        }
      }
    }

    return pluginIds;
  }

  private @Nullable IdSet createIdSet(@Nls @Nullable String title,
                                      @NonNls @NotNull String ids) {
    Ref<IdeaPluginDescriptor> firstDescriptor = Ref.create();
    Set<PluginId> pluginIds = Arrays.stream(ids.split(","))
      .distinct()
      .map(PluginId::getId)
      .filter(pluginId -> {
        IdeaPluginDescriptor descriptor = findPlugin(pluginId);
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

    /**
     * @deprecated Deprecated due to internationalization of name field
     */
    @SuppressWarnings("HardCodedStringLiteral")
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
    public Group(@NonNls @NotNull String name, @Nullable Icon icon, @Nullable String description, @NonNls @NotNull List<String> pluginIdDescription) {
      this(name, name, icon, description, pluginIdDescription);

    }
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

    public static @NotNull Group vcs() {
      return new Group("Version Controls",
                       IdeBundle.message("label.plugin.group.name.version.controls"),
                       PlatformImplIcons.VersionControls,
                       null,
                       List.of(
                         "CVS",
                         "Git4Idea",
                         "org.jetbrains.plugins.github",
                         "hg4idea",
                         "PerforceDirectPlugin",
                         "Subversion",
                         "TFS"
                       ));
    }
  }
}
