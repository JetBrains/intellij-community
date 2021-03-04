// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.WelcomeWizardUtil;
import com.intellij.ide.cloudConfig.CloudConfigProvider;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
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

  public static final String IDEA_VIM_PLUGIN_ID = "IdeaVIM";
  private static final @NlsSafe String CLOUD_DESCRIPTION = "#Cloud:#Cloud:";

  private final List<Group> myTree = new ArrayList<>();
  private final Map<String, @Nls String> myFeaturedPlugins = new LinkedHashMap<>();

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
          Set<PluginId> featuresPluginIds = getFeaturedPlugins()
            .values()
            .stream()
            .map(PluginGroupDescription::new)
            .map(PluginGroupDescription::getPluginId)
            .collect(Collectors.toUnmodifiableSet());
          MarketplaceRequests requests = MarketplaceRequests.getInstance();
          List<PluginNode> featuredPlugins = requests.loadLastCompatiblePluginDescriptors(featuresPluginIds);

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
          return Collections.emptyList();
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

    Map<String, Pair<Icon, List<String>>> treeMap = new LinkedHashMap<>();
    initGroups(treeMap, myFeaturedPlugins);
    for (Entry<String, Pair<Icon, List<String>>> entry : treeMap.entrySet()) {
      //noinspection HardCodedStringLiteral(for compile compatibilyty with deprecated api)
      myTree.add(new Group(entry.getKey(), entry.getKey(), entry.getValue().getFirst(), null, entry.getValue().getSecond()));
    }
    worker.execute();
    myDisabledPluginIds.addAll(DisabledPluginsState.loadDisabledPlugins());
    initCloudPlugins();
  }

  public void setLoadingCallback(Runnable loadingCallback) {
    myLoadingCallback = loadingCallback;
    if (!myPluginsFromRepository.isEmpty()) {
      myLoadingCallback.run();
    }
  }

  private void initCloudPlugins() {
    CloudConfigProvider provider = CloudConfigProvider.getProvider();
    if (provider == null) {
      return;
    }

    List<PluginId> plugins = provider.getInstalledPlugins();
    if (plugins.isEmpty()) {
      return;
    }

    for (Iterator<Entry<String, String>> I = myFeaturedPlugins.entrySet().iterator(); I.hasNext(); ) {
      String value = I.next().getValue();
      if (ContainerUtil.find(plugins, plugin -> value.endsWith(":" + plugin)) != null) {
        I.remove();
      }
    }

    for (PluginId plugin : plugins) {
      myFeaturedPlugins.put(plugin.getIdString(), CLOUD_DESCRIPTION + plugin);
    }
  }

  /**
   * @deprecated use {@link #initGroups(List, Map)} instead
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  protected void initGroups(Map<String, Pair<Icon, List<String>>> tree, Map<String, String> featuredPlugins) {
    initGroups(myTree, myFeaturedPlugins);
  }

  protected void initGroups(@NotNull List<? super Group> groups, @NotNull Map<String, String> featuredPlugins) {
    groups.add(
      new Group(CORE, IdeBundle.message("label.plugin.group.name.core"), null, null,
                Arrays.asList(
                  "com.intellij.copyright",
                  "com.intellij.java-i18n",
                  "org.intellij.intelliLang",
                  "com.intellij.properties",
                  "Refactor-X",//?
                  "Type Migration",
                  "ZKM"
                )));
    groups.add(
      new Group("Java Frameworks",
                IdeBundle.message("label.plugin.group.name.java.frameworks"),
                PlatformImplIcons.JavaFrameworks,
                null,
                Arrays.asList(
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
                )));
    groups.add(
      new Group("Build Tools", IdeBundle.message("label.plugin.group.name.build.tools"), PlatformImplIcons.BuildTools, null,
                Arrays.asList(
                  "AntSupport",
                  "Maven:org.jetbrains.idea.maven,org.jetbrains.idea.maven.ext",
                  "org.jetbrains.plugins.gradle"
                )));
    groups.add(
      new Group("JavaScript Development", IdeBundle.message("label.plugin.group.name.javascript.development"),
                PlatformImplIcons.WebDevelopment,
                IdeBundle.message("label.plugin.group.description.javascript.development"),
                Arrays.asList(
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
                )));

    addVcsGroup(groups);

    groups.add(
      new Group("Test Tools", IdeBundle.message("label.plugin.group.name.test.tools"), PlatformImplIcons.TestTools, null, Arrays.asList(
        "JUnit",
        "TestNG-J",
        "cucumber-java",
        "cucumber",
        "Coverage:Coverage,Emma"
      )));
    groups.add(
      new Group("Application Servers",
                IdeBundle.message("label.plugin.group.name.application.servers"),
                PlatformImplIcons.ApplicationServers,
                null,
                Arrays.asList(
                  "com.intellij.javaee.view",
                  "Geronimo",
                  "GlassFish",
                  "JBoss",
                  "Jetty",
                  "Tomcat",
                  "Weblogic",
                  "WebSphere",
                  "JSR45Plugin"
                )));
    //myTree.put("Groovy", Arrays.asList("org.intellij.grails"));
    //TODO Scala -> Play 2.x (Play 2.0 Support)
    groups
      .add(new Group("Swing", IdeBundle.message("label.plugin.group.name.swing"), PlatformImplIcons.Swing, null, Collections.singletonList(
        "com.intellij.uiDesigner"//TODO JavaFX?
      )));
    groups.add(new Group("Android", IdeBundle.message("label.plugin.group.name.android"), PlatformImplIcons.Android, null, Arrays.asList(
      "org.jetbrains.android",
      "com.intellij.android-designer")));
    groups.add(
      new Group("Database Tools", IdeBundle.message("label.plugin.group.name.database.tools"), PlatformImplIcons.DatabaseTools, null,
                Collections.singletonList(
                  "com.intellij.database"
                )));
    groups.add(
      new Group("Other Tools", IdeBundle.message("label.plugin.group.name.other.tools"), PlatformImplIcons.OtherTools, null, Arrays.asList(
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
      )));
    groups.add(new Group("Plugin Development", IdeBundle.message("label.plugin.group.name.plugin.development"), PlatformImplIcons.PluginDevelopment, null, Collections.singletonList("DevKit")));

    initFeaturedPlugins(featuredPlugins);
  }

  protected void initFeaturedPlugins(@NotNull Map<String, String> featuredPlugins) {
    featuredPlugins.put("Scala", "Custom Languages:Plugin for Scala language support:org.intellij.scala");
    featuredPlugins.put("Live Edit Tool",
                        "Web Development:Provides live edit HTML/CSS/JavaScript:com.intellij.plugins.html.instantEditing");
    addVimPlugin(featuredPlugins);
    featuredPlugins.put("Atlassian Connector",
                        "Tools Integration:Integration for Atlassian JIRA, Bamboo, Crucible, FishEye:atlassian-idea-plugin");
    addTrainingPlugin(featuredPlugins);
  }

  public static final class PluginGroupDescription {

    // TODO store fields separately,
    //      move featuredPlugins type to Map<String, PluginGroupDescription>
    private final @NotNull String @NotNull [] myStrings;

    public PluginGroupDescription(@NotNull @NonNls String idString,
                                  @NotNull @Nls String topic,
                                  @NotNull @Nls String description) {
      myStrings = new String[]{topic, description, idString};
    }

    public PluginGroupDescription(@NotNull @Nls String string) {
      myStrings = string.split(":", 3);
    }

    public @NotNull @Nls String getTopic() {
      return myStrings[0];
    }

    public @NotNull @Nls String getDescription() {
      return myStrings[1];
    }

    public @NotNull PluginId getPluginId() {
      return PluginId.getId(myStrings[2]);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PluginGroupDescription that = (PluginGroupDescription)o;
      return Arrays.equals(myStrings, that.myStrings);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(myStrings);
    }

    @Override
    public String toString() {
      return StringUtil.join(myStrings, ":");
    }
  }

  /**
   * @deprecated Please migrate to {@link PluginGroupDescription}.
   */
  @Deprecated(since = "2020.2", forRemoval = true)
  public static @NotNull @NonNls String parsePluginId(@NotNull @Nls String string) {
    return new PluginGroupDescription(string)
      .getPluginId()
      .getIdString();
  }

  protected static void addVcsGroup(@NotNull List<? super Group> groups) {
    groups.add(
      new Group("Version Controls", IdeBundle.message("label.plugin.group.name.version.controls"), PlatformImplIcons.VersionControls, null,
                Arrays.asList(
                  "CVS",
                  "Git4Idea",
                  "org.jetbrains.plugins.github",
                  "hg4idea",
                  "PerforceDirectPlugin",
                  "Subversion",
      "TFS"
    )));
  }

  public static void addVimPlugin(Map<String, String> featuredPlugins) {
    featuredPlugins.put("IdeaVim", "Editor:Emulates Vim editor:" + IDEA_VIM_PLUGIN_ID);
  }

  public static void addAwsPlugin(Map<String, String> featuredPlugins) {
    featuredPlugins.put("AWS Toolkit",
                        "Cloud Support:Create, test, and debug serverless applications built using the AWS Serverless Application Model:aws.toolkit");
  }

  public static void addTrainingPlugin(Map<String, String> featuredPlugins) {
    featuredPlugins.put("IDE Features Trainer", "Code tools:Learn basic shortcuts and essential features interactively:training");
  }

  protected static void addLuaPlugin(Map<String, String> featuredPlugins) {
    featuredPlugins.put("Lua", "Custom Languages:Lua language support:Lua");
  }

  public static void addRustPlugin(Map<String, String> featuredPlugins) {
    featuredPlugins.put("Rust", "Custom Languages:Rust language support:org.rust.lang");
  }

  public static void addMarkdownPlugin(Map<String, String> featuredPlugins) {
    featuredPlugins.put("Markdown", "Custom Languages:Markdown language support:org.intellij.plugins.markdown");
  }

  public static void addRPlugin(Map<String, String> featuredPlugins) {
    featuredPlugins.put("R", "Custom Languages:R language support:R4Intellij");
  }

  protected static void addConfigurationServerPlugin(Map<String, String> featuredPlugins) {
    featuredPlugins.put("Configuration Server",
                        "Team Work:Supports sharing settings between installations of IntelliJ Platform based products used by the same developer on different computers:IdeaServerPlugin");
  }

  public static void addTeamCityPlugin(Map<String, String> featuredPlugins) {
    featuredPlugins.put("TeamCity Integration",
                        "Tools Integration:Integration with JetBrains TeamCity - innovative solution for continuous integration and build management:JetBrains TeamCity Plugin");
  }

  public static void addBigDataToolsPlugin(@NotNull Map<String, String> featuredPlugins) {
    featuredPlugins.put("Big Data Tools", "Tools Integration:Zeppelin notebooks and Spark applications support:com.intellij.bigdatatools");
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

  public Map<String, @Nls String> getFeaturedPlugins() {
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
  }
}
