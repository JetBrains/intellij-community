// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import com.intellij.ide.WelcomeWizardUtil;
import com.intellij.ide.cloudConfig.CloudConfigProvider;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.RepositoryHelper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import icons.PlatformImplIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

public class PluginGroups {
  static final String CORE = "Core";
  private static final int MAX_DESCR_LENGTH = 55;

  static final String IDEA_VIM_PLUGIN_ID = "IdeaVIM";

  private final Map<String, Pair<Icon, List<String>>> myTree = new LinkedHashMap<>();
  private final Map<String, String> myFeaturedPlugins = new LinkedHashMap<>();

  private final Map<String, List<IdSet>> myGroups = new LinkedHashMap<>();
  private final Map<String, String> myDescriptions = new LinkedHashMap<>();
  private final List<IdeaPluginDescriptor> myPluginsFromRepository = new ArrayList<>();
  private final Collection<PluginId> myDisabledPluginIds = new HashSet<>();
  private final List<? extends IdeaPluginDescriptor> myAllPlugins;
  private boolean myInitialized;
  private final Set<String> myFeaturedIds = new HashSet<>();
  private Runnable myLoadingCallback;

  public PluginGroups() {
    myAllPlugins = PluginManagerCore.loadUncachedDescriptors();
    SwingWorker worker = new SwingWorker<List<IdeaPluginDescriptor>, Object>() {
      @Override
      protected List<IdeaPluginDescriptor> doInBackground() {
        try {
          return RepositoryHelper.loadPlugins(null);
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
    worker.execute();
    PluginManagerCore.loadDisabledPlugins(new File(PathManager.getConfigPath()).getPath(), myDisabledPluginIds);

    initGroups(myTree, myFeaturedPlugins);
    initCloudPlugins();
  }

  void setLoadingCallback(Runnable loadingCallback) {
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
      myFeaturedPlugins.put(plugin.getIdString(), "#Cloud:#Cloud:" + plugin);
    }
  }

  protected void
  initGroups(Map<String, Pair<Icon, List<String>>> tree, Map<String, String> featuredPlugins) {
    tree.put(CORE, Pair.create(null, Arrays.asList(
      "com.intellij.copyright",
      "com.intellij.java-i18n",
      "org.intellij.intelliLang",
      "com.intellij.properties",
      "Refactor-X",//?
      "Type Migration",
      "ZKM"
    )));
    tree.put("Java Frameworks", Pair.create(PlatformImplIcons.JavaFrameworks, Arrays.asList(
      "com.intellij.appengine",
      "org.intellij.grails",
      "com.intellij.gwt",
      "JBoss Seam:com.intellij.seam,com.intellij.seam.pages,com.intellij.seam.pageflow",
      "JBoss jBPM:JBPM",
      "Struts:com.intellij.struts2",
      "com.intellij.hibernate",

      "Spring:com.intellij.spring.batch," +
      "com.intellij.spring.data," +
      "com.intellij.spring.integration," +
      "com.intellij.spring.osgi," +
      "com.intellij.spring.security," +
      "com.intellij.spring," +
      "com.intellij.spring.webflow," +
      "com.intellij.spring.ws,com.intellij.aop",

      "com.intellij.micronaut",
      "com.intellij.quarkus",
      "com.intellij.helidon",

      "Java EE:com.intellij.javaee.batch," +
      "com.intellij.beanValidation," +
      "com.intellij.cdi," +
      "com.intellij.javaee," +
      "com.intellij.jsf," +
      "com.intellij.javaee.extensions," +
      "com.jetbrains.restWebServices," +
      "Web Services (JAX-WS)," +
      "com.intellij.javaee.webSocket," +
      "com.intellij.jsp," +
      "com.intellij.persistence",
      "com.intellij.freemarker",
      "com.intellij.tapestry",
      "com.intellij.velocity",
      "Guice",
      "com.intellij.aspectj",
      "Osmorc"
    )));
    tree.put("Build Tools", Pair.create(PlatformImplIcons.BuildTools, Arrays.asList(
      "AntSupport",
      "Maven:org.jetbrains.idea.maven,org.jetbrains.idea.maven.ext",
      "org.jetbrains.plugins.gradle"
    )));
    tree.put("Web Development", Pair.create(PlatformImplIcons.WebDevelopment, Arrays.asList(
      "HTML:HtmlTools,W3Validators",
      "org.jetbrains.plugins.haml",
      "com.jetbrains.plugins.jade",
      "com.intellij.css",
      "org.jetbrains.plugins.less",
      "org.jetbrains.plugins.sass",
      "org.jetbrains.plugins.stylus",
      "JavaScript:JavaScript,JavaScriptDebugger,JSIntentionPowerPack",//TODO: Inspection-JS
      "org.coffeescript",
      "com.intellij.flex",
      "com.intellij.plugins.html.instantEditing",
      "com.jetbrains.restClient",
      "com.intellij.swagger"
    )));

    addVcsGroup(tree);

    tree.put("Test Tools", Pair.create(PlatformImplIcons.TestTools, Arrays.asList(
      "JUnit",
      "TestNG-J",
      "cucumber-java",
      "cucumber",
      "Coverage:Coverage,Emma"
    )));
    tree.put("Application Servers", Pair.create(PlatformImplIcons.ApplicationServers, Arrays.asList(
      "com.intellij.javaee.view",
      "Geronimo",
      "GlassFish",
      "JBoss",
      "Jetty",
      "Resin",
      "Tomcat",
      "Weblogic",
      "WebSphere",
      "com.intellij.dmserver",
      "JSR45Plugin"
    )));
    tree.put("Clouds", Pair.create(PlatformImplIcons.Clouds, Collections.singletonList(
      "CloudFoundry"
    )));
    //myTree.put("Groovy", Arrays.asList("org.intellij.grails"));
    //TODO Scala -> Play 2.x (Play 2.0 Support)
    tree.put("Swing", Pair.create(PlatformImplIcons.Swing, Collections.singletonList(
      "com.intellij.uiDesigner"//TODO JavaFX?
    )));
    tree.put("Android", Pair.create(PlatformImplIcons.Android, Arrays.asList(
      "org.jetbrains.android",
      "com.intellij.android-designer")));
    tree.put("Database Tools", Pair.create(PlatformImplIcons.DatabaseTools, Collections.singletonList(
      "com.intellij.database"
    )));
    tree.put("Other Tools", Pair.create(PlatformImplIcons.OtherTools, Arrays.asList(
      "ByteCodeViewer",
      "com.intellij.dsm",
      "org.jetbrains.idea.eclipse",
      "org.jetbrains.debugger.streams",
      "Remote Access:com.jetbrains.plugins.webDeployment,org.jetbrains.plugins.remote-run",
      "Task Management:com.intellij.tasks,com.intellij.tasks.timeTracking",
      "org.jetbrains.plugins.terminal",
      "com.intellij.diagram",
      "org.jetbrains.plugins.yaml",
      "XSLT and XPath:XPathView,XSLT-Debugger"
    )));
    tree.put("Plugin Development", Pair.create(PlatformImplIcons.PluginDevelopment, Collections.singletonList("DevKit")));

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

  protected static void addVcsGroup(Map<String, Pair<Icon, List<String>>> tree) {
    tree.put("Version Controls", Pair.create(PlatformImplIcons.VersionControls, Arrays.asList(
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

  public static void addTrainingPlugin(Map<String, String> featuredPlugins) {
    featuredPlugins.put("IDE Features Trainer", "Code tools:Learn basic shortcuts and essential IDE features with quick interactive exercises:training");
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
                        "Tools Integration:Integration with JetBrains TeamCity - innovative solution for continuous integration and build management:Jetbrains TeamCity Plugin");
  }

  private void initIfNeeded() {
    if (myInitialized) return;
    myInitialized = true;
    for (Entry<String, Pair<Icon, List<String>>> entry : myTree.entrySet()) {
      final String group = entry.getKey();
      if (CORE.equals(group)) continue;

      List<IdSet> idSets = new ArrayList<>();
      StringBuilder description = new StringBuilder();
      for (String idDescription : entry.getValue().getSecond()) {
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
      description.insert(0, "<html><body><center><i>");
      myDescriptions.put(group, description.toString());
    }
  }

  Map<String, Pair<Icon, List<String>>> getTree() {
    initIfNeeded();
    return myTree;
  }

  Map<String, String> getFeaturedPlugins() {
    initIfNeeded();
    return myFeaturedPlugins;
  }

  public String getDescription(String group) {
    initIfNeeded();
    return myDescriptions.get(group);
  }

  public List<IdSet> getSets(String group) {
    initIfNeeded();
    return myGroups.get(group);
  }

  @Nullable
  IdeaPluginDescriptor findPlugin(@NotNull PluginId id) {
    for (IdeaPluginDescriptor pluginDescriptor : myAllPlugins) {
      if (pluginDescriptor.getPluginId() == id) {
        return pluginDescriptor;
      }
    }
    return null;
  }

  boolean isIdSetAllEnabled(IdSet set) {
    for (PluginId id : set.getIds()) {
      if (!isPluginEnabled(id)) {
        return false;
      }
    }
    return true;
  }

  void setIdSetEnabled(@NotNull IdSet set, boolean enabled) {
    for (PluginId id : set.getIds()) {
      setPluginEnabledWithDependencies(id, enabled);
    }
  }

  @NotNull
  Collection<PluginId> getDisabledPluginIds() {
    return Collections.unmodifiableCollection(myDisabledPluginIds);
  }

  List<IdeaPluginDescriptor> getPluginsFromRepository() {
    return myPluginsFromRepository;
  }

  boolean isPluginEnabled(@NotNull PluginId pluginId) {
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

  void setPluginEnabledWithDependencies(@NotNull PluginId pluginId, boolean enabled) {
    initIfNeeded();
    Set<PluginId> ids = new HashSet<>();
    collectInvolvedIds(pluginId, enabled, ids);
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

  private void collectInvolvedIds(PluginId pluginId, boolean toEnable, Set<PluginId> ids) {
    ids.add(pluginId);
    if (toEnable) {
      for (PluginId id : getNonOptionalDependencies(pluginId)) {
        collectInvolvedIds(id, true, ids);
      }
    }
    else {
      Condition<PluginId> condition = id -> pluginId == id;
      for (final IdeaPluginDescriptor plugin : myAllPlugins) {
        if (null != ContainerUtil.find(plugin.getDependentPluginIds(), condition) &&
            null == ContainerUtil.find(plugin.getOptionalDependentPluginIds(), condition)) {
          collectInvolvedIds(plugin.getPluginId(), false, ids);
        }
      }
    }
  }

  @NotNull
  private List<PluginId> getNonOptionalDependencies(PluginId id) {
    List<PluginId> result = new ArrayList<>();
    IdeaPluginDescriptor descriptor = findPlugin(id);
    if (descriptor != null) {
      for (PluginId pluginId : descriptor.getDependentPluginIds()) {
        if (pluginId == PluginManagerCore.CORE_ID) {
          continue;
        }
        if (!ArrayUtil.contains(pluginId, descriptor.getOptionalDependentPluginIds())) {
          result.add(pluginId);
        }
      }
    }
    return result;
  }
}
