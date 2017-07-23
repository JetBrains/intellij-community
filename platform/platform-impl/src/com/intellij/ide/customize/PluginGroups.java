/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import icons.PlatformImplIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

public class PluginGroups {
  static final String CORE = "Core";
  private static final int MAX_DESCR_LENGTH = 55;
  
  public static final String IDEA_VIM_PLUGIN_ID = "IdeaVIM";

  final Map<String, Pair<Icon, List<String>>> myTree = new LinkedHashMap<>();
  final Map<String, String> myFeaturedPlugins = new LinkedHashMap<>();

  private final Map<String, List<IdSet>> myGroups = new LinkedHashMap<>();
  private final Map<String, String> myDescriptions = new LinkedHashMap<>();
  private final List<IdeaPluginDescriptor> myPluginsFromRepository = new ArrayList<>();
  private Collection<String> myDisabledPluginIds = new HashSet<>();
  private IdeaPluginDescriptor[] myAllPlugins;
  private boolean myInitialized = false;
  private Set<String> myFeaturedIds = new HashSet<>();
  private Runnable myLoadingCallback = null;

  public PluginGroups() {
    myAllPlugins = PluginManagerCore.loadDescriptors(null, ContainerUtil.newArrayList());
    SwingWorker worker = new SwingWorker<List<IdeaPluginDescriptor>, Object>() {
      @Override
      protected List<IdeaPluginDescriptor> doInBackground() throws Exception {
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

    List<String> plugins = provider.getInstalledPlugins();
    if (plugins.isEmpty()) {
      return;
    }

    for (Iterator<Entry<String, String>> I = myFeaturedPlugins.entrySet().iterator(); I.hasNext(); ) {
      String value = I.next().getValue();
      if (ContainerUtil.find(plugins, plugin -> value.endsWith(":" + plugin)) != null) {
        I.remove();
      }
    }

    for (String plugin : plugins) {
      myFeaturedPlugins.put(plugin, "#Cloud:#Cloud:" + plugin);
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
      "com.intellij.vaadin",
      "JBoss Seam:com.intellij.seam,com.intellij.seam.pages,com.intellij.seam.pageflow",
      "JBoss jBPM:JBPM",
      "Struts:StrutsAssistant,com.intellij.struts2",
      "com.intellij.hibernate",
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
      "HTML:HtmlTools,QuirksMode,W3Validators",
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
      "com.jetbrains.restClient"
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
    tree.put("Clouds", Pair.create(PlatformImplIcons.Clouds, Arrays.asList(
      "CloudFoundry",
      "CloudBees",
      "Heroku",
      "OpenShift"
    )));
    //myTree.put("Groovy", Arrays.asList("org.intellij.grails"));
    //TODO Scala -> Play 2.x (Play 2.0 Support)
    tree.put("Swing", Pair.create(PlatformImplIcons.Swing, Arrays.asList(
      "com.intellij.uiDesigner"//TODO JavaFX?
    )));
    tree.put("Android", Pair.create(PlatformImplIcons.Android, Arrays.asList(
      "org.jetbrains.android",
      "com.intellij.android-designer")));
    tree.put("Database Tools", Pair.create(PlatformImplIcons.DatabaseTools, Arrays.asList(
      "com.intellij.database"
    )));
    tree.put("Other Tools", Pair.create(PlatformImplIcons.OtherTools, Arrays.asList(
      "ByteCodeViewer",
      "com.intellij.dsm",
      "org.jetbrains.idea.eclipse",
      "Remote Access:com.jetbrains.plugins.webDeployment,org.jetbrains.plugins.remote-run",
      "Task Management:com.intellij.tasks,com.intellij.tasks.timeTracking",
      "org.jetbrains.plugins.terminal",
      "com.intellij.diagram",
      "org.jetbrains.plugins.yaml",
      "XSLT and XPath:XPathView,XSLT-Debugger"
    )));
    tree.put("Plugin Development", Pair.create(PlatformImplIcons.PluginDevelopment, Arrays.asList("DevKit")));

    initFeaturedPlugins(featuredPlugins);
  }

  protected void initFeaturedPlugins(Map<String, String> featuredPlugins) {
    featuredPlugins.put("Scala", "Custom Languages:Plugin for Scala language support:org.intellij.scala");
    featuredPlugins.put("Live Edit Tool",
                        "Web Development:Provides live edit HTML/CSS/JavaScript:com.intellij.plugins.html.instantEditing");
    addVimPlugin(featuredPlugins);
    featuredPlugins.put("NodeJS", "JavaScript:Node.js integration:NodeJS");
    featuredPlugins.put("Atlassian Connector",
                        "Tools Integration:Integration for Atlassian JIRA, Bamboo, Crucible, FishEye:atlassian-idea-plugin");
  }

  public static void addVcsGroup(Map<String, Pair<Icon, List<String>>> tree) {
    tree.put("Version Controls", Pair.create(PlatformImplIcons.VersionControls, Arrays.asList(
      "ClearcasePlugin",
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

  public static void addLuaPlugin(Map<String, String> featuredPlugins) {
    featuredPlugins.put("Lua", "Custom Languages:Lua language support:Lua");
  }

  public static void addGoPlugin(Map<String, String> featuredPlugins) {
    featuredPlugins.put("Go", "Custom Languages:Go language support:org.jetbrains.plugins.go");
  }

  public static void addMarkdownPlugin(Map<String, String> featuredPlugins) {
    featuredPlugins.put("Markdown", "Custom Languages:Markdown language support:org.intellij.plugins.markdown");
  }

  public static void addConfigurationServerPlugin(Map<String, String> featuredPlugins) {
    featuredPlugins.put("Configuration Server",
                        "Team Work:Supports sharing settings between installations of IntelliJ Platform based products used by the same developer on different computers:IdeaServerPlugin");
  }

  public static void addTeamCityPlugin(Map<String, String> featuredPlugins) {
    featuredPlugins.put("TeamCity Integration",
                        "Tools Integration:Integration with JetBrains TeamCity - innovative solution for continuous integration and build management:Jetbrains TeamCity Plugin");
  }

  private void initIfNeed() {
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
    initIfNeed();
    return myTree;
  }

  Map<String, String> getFeaturedPlugins() {
    initIfNeed();
    return myFeaturedPlugins;
  }

  public String getDescription(String group) {
    initIfNeed();
    return myDescriptions.get(group);
  }

  public List<IdSet> getSets(String group) {
    initIfNeed();
    return myGroups.get(group);
  }

  @Nullable
  IdeaPluginDescriptor findPlugin(String id) {
    for (IdeaPluginDescriptor pluginDescriptor : myAllPlugins) {
      PluginId pluginId = pluginDescriptor.getPluginId();
      if (pluginId != null && StringUtil.equals(pluginId.getIdString(), id)) {
        return pluginDescriptor;
      }
    }
    return null;
  }

  boolean isIdSetAllEnabled(IdSet set) {
    for (String id : set.getIds()) {
      if (!isPluginEnabled(id)) return false;
    }
    return true;
  }

  void setIdSetEnabled(IdSet set, boolean enabled) {
    for (String id : set.getIds()) {
      setPluginEnabledWithDependencies(id, enabled);
    }
  }

  public Collection<String> getDisabledPluginIds() {
    return Collections.unmodifiableCollection(myDisabledPluginIds);
  }

  public List<IdeaPluginDescriptor> getPluginsFromRepository() {
    return myPluginsFromRepository;
  }

  boolean isPluginEnabled(String pluginId) {
    initIfNeed();
    return !myDisabledPluginIds.contains(pluginId);
  }

  IdSet getSet(String pluginId) {
    initIfNeed();
    for (List<IdSet> sets : myGroups.values()) {
      for (IdSet set : sets) {
        for (String id : set.getIds()) {
          if (id.equals(pluginId)) return set;
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

  void setPluginEnabledWithDependencies(final String pluginId, boolean enabled) {
    initIfNeed();
    Set<String> ids = new HashSet<>();
    collectInvolvedIds(pluginId, enabled, ids);
    Set<IdSet> sets = new HashSet<>();
    for (String id : ids) {
      IdSet set = getSet(id);
      if (set != null) {
        sets.add(set);
      }
    }
    for (IdSet set : sets) {
      ids.addAll(Arrays.asList(set.getIds()));
    }
    for (String id : ids) {
      if (enabled) {
        myDisabledPluginIds.remove(id);
      }
      else {
        myDisabledPluginIds.add(id);
      }
    }
  }

  private void collectInvolvedIds(final String pluginId, boolean toEnable, Set<String> ids) {
    ids.add(pluginId);
    if (toEnable) {
      for (String id : getNonOptionalDependencies(pluginId)) {
        collectInvolvedIds(id, true, ids);
      }
    }
    else {
      Condition<PluginId> condition = id -> pluginId.equals(id.getIdString());
      for (final IdeaPluginDescriptor plugin : myAllPlugins) {
        if (null != ContainerUtil.find(plugin.getDependentPluginIds(), condition) &&
            null == ContainerUtil.find(plugin.getOptionalDependentPluginIds(), condition)) {
          collectInvolvedIds(plugin.getPluginId().getIdString(), false, ids);
        }
      }
    }
  }

  private List<String> getNonOptionalDependencies(final String id) {
    List<String> result = new ArrayList<>();
    IdeaPluginDescriptor descriptor = findPlugin(id);
    if (descriptor != null) {
      for (PluginId pluginId : descriptor.getDependentPluginIds()) {
        if (pluginId.getIdString().equals("com.intellij")) continue;
        if (!ArrayUtil.contains(pluginId, descriptor.getOptionalDependentPluginIds())) {
          result.add(pluginId.getIdString());
        }
      }
    }
    return result;
  }
}
