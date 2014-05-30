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
package com.intellij.ide.customize;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.RepositoryHelper;
import com.intellij.idea.StartupUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

class PluginGroups {
  static final String CORE = "Core";
  private static final int MAX_DESCR_LENGTH = 55;


  private static PluginGroups instance = null;

  final Map<String, Pair<String, List<String>>> myTree = new LinkedHashMap<String, Pair<String, List<String>>>();
  final Map<String, String> myFeaturedPlugins = new LinkedHashMap<String, String>();

  private final Map<String, List<IdSet>> myGroups = new LinkedHashMap<String, List<IdSet>>();
  private final Map<String, String> myDescriptions = new LinkedHashMap<String, String>();
  private final List<IdeaPluginDescriptor> myPluginsFromRepository = new ArrayList<IdeaPluginDescriptor>();
  private Collection<String> myDisabledPluginIds = new HashSet<String>();
  private IdeaPluginDescriptor[] myAllPlugins;
  private boolean myInitialized = false;
  private Set<String> myFeaturedIds = new HashSet<String>();


  static synchronized PluginGroups getInstance() {
    if (instance == null) {
      instance = new PluginGroups();
    }
    return instance;
  }

  private PluginGroups() {
    myAllPlugins = PluginManager.loadDescriptors(null);
    try {
      myPluginsFromRepository.addAll(RepositoryHelper.loadPluginsFromRepository(null));
    }
    catch (Exception e) {
      //OK, it's offline
    }
    PluginManager.loadDisabledPlugins(new File(PathManager.getConfigPath()).getPath(), myDisabledPluginIds);


    myTree.put(CORE, Pair.create((String)null, Arrays.asList(
      "com.intellij.copyright",
      "com.intellij.java-i18n",
      "org.intellij.intelliLang",
      "com.intellij.properties",
      "Refactor-X",//?
      "Structural Search",
      "Type Migration",
      "ZKM"
    )));
    myTree.put("Java Frameworks", Pair.create("/plugins/JavaFrameworks.png", Arrays.asList(
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
      "J2EE:com.intellij.javaee.batch," +
      "com.intellij.beanValidation," +
      "com.intellij.cdi," +
      "com.intellij.javaee," +
      "com.intellij.jsf," +
      "com.intellij.javaee.extensions," +
      "com.jetbrains.restWebServices," +
      "Java EE: Web Services (JAX-WS)," +
      "com.intellij.javaee.webSocket," +
      "com.intellij.jsp," +
      "com.intellij.persistence",
      "com.intellij.freemarker",
      "com.intellij.tapestry",
      "com.intellij.velocity",
      "GuiceyIDEA",
      "com.intellij.aspectj",
      "Osmorc"
    )));
    myTree.put("Build Tools", Pair.create("/plugins/BuildTools.png", Arrays.asList(
      "AntSupport",
      "Maven:org.jetbrains.idea.maven,org.jetbrains.idea.maven.ext",
      "org.jetbrains.plugins.gradle"
    )));
    myTree.put("Web Development", Pair.create("/plugins/WebDevelopment.png", Arrays.asList(
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
    myTree.put("Version Controls", Pair.create("/plugins/VersionControls.png", Arrays.asList(
      "ClearcasePlugin",
      "CVS",
      "Git4Idea",
      "org.jetbrains.plugins.github",
      "hg4idea",
      "PerforceDirectPlugin",
      "Subversion",
      "TFS"
    )));
    myTree.put("Test Tools", Pair.create("/plugins/TestTools.png", Arrays.asList(
      "JUnit",
      "TestNG-J",
      "cucumber-java",
      "cucumber",
      "Coverage:Coverage,Emma"
    )));
    myTree.put("Application Servers", Pair.create("/plugins/ApplicationServers.png", Arrays.asList(
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
    myTree.put("Clouds", Pair.create("/plugins/Clouds.png", Arrays.asList(
      "CloudFoundry",
      "CloudBees",
      "Heroku",
      "OpenShift"
    )));
    //myTree.put("Groovy", Arrays.asList("org.intellij.grails"));
    //TODO Scala -> Play 2.x (Play 2.0 Support)
    myTree.put("Swing", Pair.create("/plugins/Swing.png", Arrays.asList(
      "com.intellij.uiDesigner"//TODO JavaFX?
    )));
    myTree.put("Android", Pair.create("/plugins/Android.png", Arrays.asList(
      "org.jetbrains.android",
      "com.intellij.android-designer")));
    myTree.put("Database Tools", Pair.create("/plugins/DatabaseTools.png", Arrays.asList(
      "com.intellij.sql",
      "com.intellij.persistence.database"
    )));
    myTree.put("Other Tools", Pair.create("/plugins/OtherTools.png", Arrays.asList(
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
    myTree.put("Plugin Development", Pair.create("/plugins/PluginDevelopment.png", Arrays.asList("DevKit")));

    myFeaturedPlugins.put("Scala", "Custom Languages:Plugin for Scala language support:org.intellij.scala");
    myFeaturedPlugins.put("Live Edit Tool", "Web Development:Provides live edit HTML/CSS/JavaScript:com.intellij.plugins.html.instantEditing");
    myFeaturedPlugins.put("IdeaVIM", "Editor:Vim emulation plug-in for IDEs based on the IntelliJ platform:IdeaVIM");
    myFeaturedPlugins.put("NodeJS", "JavaScript:Node.js integration:NodeJS");
    myFeaturedPlugins.put("Atlassian Connector", "Tools Integration:Integration for Atlassian JIRA, Bamboo, Cricible, FishEye:atlassian-idea-plugin");

  }

  private void initIfNeed() {
    if (myInitialized) return;
    myInitialized = true;
    for (Map.Entry<String, Pair<String, List<String>>> entry : myTree.entrySet()) {
      final String group = entry.getKey();
      if (CORE.equals(group)) continue;

      List<IdSet> idSets = new ArrayList<IdSet>();
      StringBuilder description = new StringBuilder();
      for (String idDescription : entry.getValue().getSecond()) {
        IdSet idSet = new IdSet(idDescription);
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

  Map<String, Pair<String, List<String>>> getTree() {
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
    } else {
      myFeaturedIds.remove(pluginId);
    }
    StartupUtil.setFeaturedPluginsToInstall(myFeaturedIds);
  }

  void setPluginEnabledWithDependencies(final String pluginId, boolean enabled) {
    initIfNeed();
    Set<String> ids = new HashSet<String>();
    collectInvolvedIds(pluginId, enabled, ids);
    Set<IdSet> sets = new HashSet<IdSet>();
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
      Condition<PluginId> condition = new Condition<PluginId>() {
        @Override
        public boolean value(PluginId id) {
          return pluginId.equals(id.getIdString());
        }
      };
      for (final IdeaPluginDescriptor plugin : myAllPlugins) {
        if (null != ContainerUtil.find(plugin.getDependentPluginIds(), condition) &&
            null == ContainerUtil.find(plugin.getOptionalDependentPluginIds(), condition)) {
          collectInvolvedIds(plugin.getPluginId().getIdString(), false, ids);
        }
      }
    }
  }

  private List<String> getNonOptionalDependencies(final String id) {
    List<String> result = new ArrayList<String>();
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
