// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.WelcomeWizardUtil;
import com.intellij.ide.cloudConfig.CloudConfigProvider;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.NlsSafe;
import icons.PlatformImplIcons;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
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
  private final Set<String> myFeaturedIds = new HashSet<>();
  private Runnable myLoadingCallback;

  public PluginGroups() {
    myAllPlugins = PluginDescriptorLoader.loadUncachedDescriptors();
    SwingWorker<List<PluginNode>, Object> worker = new SwingWorker<>() {
      @Override
      protected @NotNull List<PluginNode> doInBackground() {
        try {
          MarketplaceRequests requests = MarketplaceRequests.getInstance();
          List<PluginNode> featuredPlugins = requests.loadLastCompatiblePluginDescriptors(myFeaturedPlugins.keySet());

          Set<PluginId> dependsIds = featuredPlugins
            .stream()
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
    for (PluginGroupDescription description : getInitialFeaturedPlugins()) {
      myFeaturedPlugins.put(description.getPluginId(), description);
    }

    // todo to be removed
    Map<String, String> tempFeaturedPlugins = new LinkedHashMap<>();
    initGroups(myTree, tempFeaturedPlugins);
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
   * @deprecated Please migrate to {@link PluginGroupDescription#teamCity()}.
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
      @Nls StringBuilder description = new StringBuilder();
      for (String idDescription : g.getPluginIdDescription()) {
        IdSet idSet = new IdSet(this, idDescription);
        String idSetTitle = idSet.getTitle();
        if (idSetTitle == null) continue;
        idSets.add(idSet);
        if (description.length() > 0) {
          description.append(", ");
        }
        description.append(idSetTitle);
      }
      myGroups.put(group, idSets);

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
      if (pluginDescriptor.getPluginId() == id) {
        return pluginDescriptor;
      }
    }
    return null;
  }

  public boolean isIdSetAllEnabled(IdSet set) {
    for (PluginId id : set.getIds()) {
      if (!isPluginEnabled(id)) {
        return false;
      }
    }
    return true;
  }

  public void setIdSetEnabled(@NotNull IdSet set, boolean enabled) {
    for (PluginId id : set.getIds()) {
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

  private IdSet getSet(@NotNull PluginId pluginId) {
    initIfNeeded();
    for (List<IdSet> sets : myGroups.values()) {
      for (IdSet set : sets) {
        for (PluginId id : set.getIds()) {
          if (id == pluginId) {
            return set;
          }
        }
      }
    }
    return null;
  }

  void setFeaturedPluginEnabled(String pluginId, boolean enabled) {
    if (enabled) {
      myFeaturedIds.add(pluginId);
    }
    else {
      myFeaturedIds.remove(pluginId);
    }
    WelcomeWizardUtil.setFeaturedPluginsToInstall(myFeaturedIds);
  }

  public void setPluginEnabledWithDependencies(@NotNull PluginId pluginId, boolean enabled) {
    initIfNeeded();
    Set<PluginId> ids = new HashSet<>();
    Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptor = new HashMap<>(myAllPlugins.size());
    for (IdeaPluginDescriptorImpl pluginDescriptor : myAllPlugins) {
      idToDescriptor.put(pluginDescriptor.getPluginId(), pluginDescriptor);
    }

    collectInvolvedIds(pluginId, enabled, ids, idToDescriptor);
    Set<IdSet> sets = new HashSet<>();
    for (PluginId id : ids) {
      IdSet set = getSet(id);
      if (set != null) {
        sets.add(set);
      }
    }
    for (IdSet set : sets) {
      ids.addAll(set.getIds());
    }
    for (PluginId id : ids) {
      if (enabled) {
        myDisabledPluginIds.remove(id);
      }
      else {
        myDisabledPluginIds.add(id);
      }
    }
  }

  private void collectInvolvedIds(@NotNull PluginId pluginId,
                                  boolean toEnable,
                                  @NotNull Set<? super PluginId> ids,
                                  @NotNull Map<PluginId, IdeaPluginDescriptorImpl> idToDescriptor) {
    ids.add(pluginId);
    if (toEnable) {
      IdeaPluginDescriptorImpl descriptor = idToDescriptor.get(pluginId);
      if (descriptor != null) {
        for (PluginDependency dependency : descriptor.getPluginDependencies()) {
          if (!dependency.isOptional && dependency.id != PluginManagerCore.CORE_ID) {
            collectInvolvedIds(dependency.id, true, ids, idToDescriptor);
          }
        }
      }
    }
    else {
      for (IdeaPluginDescriptorImpl plugin : myAllPlugins) {
        for (PluginDependency dependency : plugin.getPluginDependencies()) {
          if (!dependency.isOptional && dependency.id == pluginId) {
            collectInvolvedIds(plugin.getPluginId(), false, ids, idToDescriptor);
          }
        }
      }
    }
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
