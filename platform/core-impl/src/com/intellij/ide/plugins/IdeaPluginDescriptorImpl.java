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
package com.intellij.ide.plugins;

import com.intellij.AbstractBundle;
import com.intellij.CommonBundle;
import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xmlb.JDOMXIncluder;
import com.intellij.util.xmlb.XmlSerializer;
import gnu.trove.THashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author mike
 */
public class IdeaPluginDescriptorImpl implements IdeaPluginDescriptor {
  public static final IdeaPluginDescriptorImpl[] EMPTY_ARRAY = new IdeaPluginDescriptorImpl[0];
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.plugins.PluginDescriptor");
  private final NullableLazyValue<String> myDescription = new NullableLazyValue<String>() {
    @Override
    protected String compute() {
      return computeDescription();
    }
  };

  private String myName;
  private PluginId myId;
  private String myResourceBundleBaseName;
  private String myChangeNotes;
  private String myVersion;
  private String myVendor;
  private String myVendorEmail;
  private String myVendorUrl;
  private String myVendorLogoPath;
  private String myCategory;
  private String url;
  private File myPath;
  private PluginId[] myDependencies = PluginId.EMPTY_ARRAY;
  private PluginId[] myOptionalDependencies = PluginId.EMPTY_ARRAY;
  private Map<PluginId, List<String>> myOptionalConfigs;
  private Map<PluginId, List<IdeaPluginDescriptorImpl>> myOptionalDescriptors;
  @Nullable private List<Element> myActionElements;
  private ComponentConfig[] myAppComponents;
  private ComponentConfig[] myProjectComponents;
  private ComponentConfig[] myModuleComponents;
  private boolean myDeleted;
  private ClassLoader myLoader;
  private HelpSetPath[] myHelpSets;
  @Nullable private MultiMap<String, Pair<String, Element>> myExtensions; // extension point name -> list of (extension default NS, extension element)
  @Nullable private MultiMap<String, Element> myExtensionsPoints;
  private String myDescriptionChildText;
  private String myDownloadCounter;
  private long myDate;
  private boolean myUseIdeaClassLoader;
  private boolean myUseCoreClassLoader;
  private boolean myAllowBundledUpdate;
  private boolean myEnabled = true;
  private String mySinceBuild;
  private String myUntilBuild;
  private Boolean mySkipped;
  private List<String> myModules;

  public IdeaPluginDescriptorImpl(@NotNull File pluginPath) {
    myPath = pluginPath;
  }

  @Nullable
  private static List<Element> copyChildren(@Nullable Element[] elements) {
    if (elements == null || elements.length == 0) {
      return null;
    }

    List<Element> result = new SmartList<>();
    for (Element extensionsRoot : elements) {
      result.addAll(extensionsRoot.getChildren());
    }
    return result;
  }
  @Nullable
  private static List<Pair<String, Element>> copyChildrenAndNs(@Nullable Element[] elements) {
    if (elements == null || elements.length == 0) {
      return null;
    }

    List<Pair<String, Element>> result = new SmartList<>();
    for (Element extensionsRoot : elements) {
      String ns = extensionsRoot.getAttributeValue("defaultExtensionNs");
      result.addAll(ContainerUtil.map(extensionsRoot.getChildren(), e->Pair.create(ns, e)));
    }
    return result;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private static String createDescriptionKey(final PluginId id) {
    return "plugin." + id + ".description";
  }

  private static ComponentConfig[] mergeComponents(ComponentConfig[] first, ComponentConfig[] second) {
    if (first == null) {
      return second;
    }
    if (second == null) {
      return first;
    }
    return ArrayUtil.mergeArrays(first, second);
  }

  @Override
  public File getPath() {
    return myPath;
  }

  public void setPath(@NotNull File path) {
    myPath = path;
  }

  public void readExternal(@NotNull Document document, @NotNull URL url, @NotNull JDOMXIncluder.PathResolver pathResolver) throws InvalidDataException {
    Application application = ApplicationManager.getApplication();
    readExternal(document, url, application != null && application.isUnitTestMode(), pathResolver);
  }

  public void readExternal(@NotNull Document document, @NotNull URL url, boolean ignoreMissingInclude, @NotNull JDOMXIncluder.PathResolver pathResolver) throws InvalidDataException {
    document = JDOMXIncluder.resolve(document, url.toExternalForm(), ignoreMissingInclude, pathResolver);
    Element rootElement = document.getRootElement();
    Element newElement = JDOMUtil.internElement(rootElement);
    readExternal(newElement);
  }

  public void readExternal(@NotNull URL url) throws InvalidDataException, FileNotFoundException {
    try {
      Document document = JDOMUtil.loadDocument(url);
      readExternal(document, url, JDOMXIncluder.DEFAULT_PATH_RESOLVER);
    }
    catch (FileNotFoundException e) {
      throw e;
    }
    catch (IOException | JDOMException e) {
      throw new InvalidDataException(e);
    }
  }

  // used in upsource
  protected void readExternal(@NotNull Element element) {
    final PluginBean pluginBean = XmlSerializer.deserialize(element, PluginBean.class);
    url = pluginBean.url;
    myName = pluginBean.name;
    String idString = pluginBean.id;
    if (idString == null || idString.isEmpty()) {
      idString = myName;
    }
    myId = idString == null ? null : PluginId.getId(idString);

    String internalVersionString = pluginBean.formatVersion;
    if (internalVersionString != null) {
      try {
        //noinspection ResultOfMethodCallIgnored
        Integer.parseInt(internalVersionString);
      }
      catch (NumberFormatException e) {
        LOG.error(new PluginException("Invalid value in plugin.xml format version: '" + internalVersionString + "'", e, myId));
      }
    }
    myUseIdeaClassLoader = pluginBean.useIdeaClassLoader;
    myAllowBundledUpdate = pluginBean.allowBundledUpdate;
    if (pluginBean.ideaVersion != null) {
      mySinceBuild = pluginBean.ideaVersion.sinceBuild;
      myUntilBuild = convertExplicitBigNumberInUntilBuildToStar(pluginBean.ideaVersion.untilBuild);
    }

    myResourceBundleBaseName = pluginBean.resourceBundle;

    myDescriptionChildText = pluginBean.description;
    myChangeNotes = pluginBean.changeNotes;
    myVersion = pluginBean.pluginVersion;
    if (myVersion == null) {
      myVersion = PluginManagerCore.getBuildNumber().asStringWithoutProductCode();
    }

    myCategory = pluginBean.category;


    if (pluginBean.vendor != null) {
      myVendor = pluginBean.vendor.name;
      myVendorEmail = pluginBean.vendor.email;
      myVendorUrl = pluginBean.vendor.url;
      myVendorLogoPath = pluginBean.vendor.logo;
    }

    // preserve items order as specified in xml (filterBadPlugins will not fail if module comes first)
    Set<PluginId> dependentPlugins = new LinkedHashSet<>();
    Set<PluginId> optionalDependentPlugins = new LinkedHashSet<>();
    if (pluginBean.dependencies != null) {
      myOptionalConfigs = new THashMap<>();
      for (PluginDependency dependency : pluginBean.dependencies) {
        String text = dependency.pluginId;
        if (!StringUtil.isEmpty(text)) {
          PluginId id = PluginId.getId(text);
          dependentPlugins.add(id);
          if (dependency.optional) {
            optionalDependentPlugins.add(id);
            if (!StringUtil.isEmpty(dependency.configFile)) {
              myOptionalConfigs.computeIfAbsent(id, it -> new SmartList<>()).add(dependency.configFile);
            }
          }
        }
      }
    }

    myDependencies = dependentPlugins.isEmpty() ? PluginId.EMPTY_ARRAY : dependentPlugins.toArray(new PluginId[dependentPlugins.size()]);
    myOptionalDependencies = optionalDependentPlugins.isEmpty() ? PluginId.EMPTY_ARRAY : optionalDependentPlugins.toArray(new PluginId[optionalDependentPlugins.size()]);

    if (pluginBean.helpSets == null || pluginBean.helpSets.length == 0) {
      myHelpSets = HelpSetPath.EMPTY;
    }
    else {
      myHelpSets = new HelpSetPath[pluginBean.helpSets.length];
      PluginHelpSet[] sets = pluginBean.helpSets;
      for (int i = 0, n = sets.length; i < n; i++) {
        PluginHelpSet pluginHelpSet = sets[i];
        myHelpSets[i] = new HelpSetPath(pluginHelpSet.file, pluginHelpSet.path);
      }
    }

    myAppComponents = pluginBean.applicationComponents;
    myProjectComponents = pluginBean.projectComponents;
    myModuleComponents = pluginBean.moduleComponents;

    if (myAppComponents == null) myAppComponents = ComponentConfig.EMPTY_ARRAY;
    if (myProjectComponents == null) myProjectComponents = ComponentConfig.EMPTY_ARRAY;
    if (myModuleComponents == null) myModuleComponents = ComponentConfig.EMPTY_ARRAY;

    List<Pair<String, Element>> extensions = copyChildrenAndNs(pluginBean.extensions);
    if (extensions != null) {
      myExtensions = MultiMap.createSmart();
      for (Pair<String, Element> pair : extensions) {
        String ns = pair.first;
        Element extension = pair.second;
        myExtensions.putValue(ExtensionsAreaImpl.extractEPName(extension, ns), Pair.create(ns, extension));
      }
    }

    List<Element> extensionPoints = copyChildren(pluginBean.extensionPoints);
    if (extensionPoints != null) {
      myExtensionsPoints = MultiMap.createSmart();
      for (Element extensionPoint : extensionPoints) {
        myExtensionsPoints.putValue(StringUtil.notNullize(extensionPoint.getAttributeValue(ExtensionsAreaImpl.ATTRIBUTE_AREA)), extensionPoint);
      }
    }

    myActionElements = copyChildren(pluginBean.actions);

    if (pluginBean.modules != null && !pluginBean.modules.isEmpty()) {
      myModules = pluginBean.modules;
    }
  }

  public static final Pattern EXPLICIT_BIG_NUMBER_PATTERN = Pattern.compile("(.*)\\.(9{4,}+|10{4,}+)");

  /**
   * Convert build number like '146.9999' to '146.*' (like plugin repository does) to ensure that plugins which have such values in
   * 'until-build' attribute will be compatible with 146.SNAPSHOT build.
   */
  public static String convertExplicitBigNumberInUntilBuildToStar(@Nullable String build) {
    if (build == null) return null;
    Matcher matcher = EXPLICIT_BIG_NUMBER_PATTERN.matcher(build);
    if (matcher.matches()) {
      return matcher.group(1) + ".*";
    }
    return build;
  }

  // made public for Upsource
  public void registerExtensionPoints(@NotNull ExtensionsArea area) {
    if (myExtensionsPoints != null) {
      for (Element element : myExtensionsPoints.get(StringUtil.notNullize(area.getAreaClass()))) {
        area.registerExtensionPoint(this, element);
      }
    }
  }

  // made public for Upsource
  public void registerExtensions(@NotNull ExtensionsArea area, @NotNull String epName) {
    if (myExtensions != null) {
      for (Pair<String, Element> pair : myExtensions.get(epName)) {
        Element element = pair.second;
        String ns = pair.first;
        area.registerExtension(this, element, ns);
      }
    }
  }

  @Override
  public String getDescription() {
    return myDescription.getValue();
  }

  @Override
  public String getChangeNotes() {
    return myChangeNotes;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public PluginId[] getDependentPluginIds() {
    return myDependencies;
  }

  @Override
  @NotNull
  public PluginId[] getOptionalDependentPluginIds() {
    return myOptionalDependencies;
  }

  @Override
  public String getVendor() {
    return myVendor;
  }

  public void setVendor( final String val )
  {
    myVendor = val;
  }

  @Override
  public String getVersion() {
    return myVersion;
  }

  @Override
  public String getResourceBundleBaseName() {
    return myResourceBundleBaseName;
  }

  @Override
  public String getCategory() {
    return myCategory;
  }

  /*
     This setter was explicitly defined to be able to set a category for a
     descriptor outside its loading from the xml file.
     Problem was that most commonly plugin authors do not publish the plugin's
     category in its .xml file so to be consistent in plugins representation
     (e.g. in the Plugins form) we have to set this value outside.
  */
  public void setCategory( String category ){
    myCategory = category;
  }

  @SuppressWarnings("UnusedDeclaration") // Used in Upsource
  @Nullable
  public MultiMap<String, Element> getExtensionsPoints() {
    return myExtensionsPoints;
  }

  @SuppressWarnings("UnusedDeclaration") // Used in Upsource
  @Nullable
  public MultiMap<String, Element> getExtensions() {
    if (myExtensions == null) return null;
    MultiMap<String, Element> result = MultiMap.create();

    for (Map.Entry<String, Collection<Pair<String, Element>>> entry : myExtensions.entrySet()) {
      result.put(entry.getKey(), ContainerUtil.map(entry.getValue(), p->p.second));
    }
    return result;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  @NotNull
  public List<File> getClassPath() {
    if (myPath.isDirectory()) {
      final List<File> result = new ArrayList<>();
      final File classesDir = new File(myPath, "classes");

      if (classesDir.exists()) {
        result.add(classesDir);
      }

      final File[] files = new File(myPath, "lib").listFiles();
      if (files != null && files.length > 0) {
        for (final File f : files) {
          if (f.isFile()) {
            final String name = f.getName();
            if (StringUtil.endsWithIgnoreCase(name, ".jar") || StringUtil.endsWithIgnoreCase(name, ".zip")) {
              result.add(f);
            }
          }
          else {
            result.add(f);
          }
        }
      }

      return result;
    }
    else {
      return Collections.singletonList(myPath);
    }
  }

  @Override
  @Nullable
  public List<Element> getActionsDescriptionElements() {
    return myActionElements;
  }

  @Override
  @NotNull
  public ComponentConfig[] getAppComponents() {
    return myAppComponents;
  }

  @Override
  @NotNull
  public ComponentConfig[] getProjectComponents() {
    return myProjectComponents;
  }

  @Override
  @NotNull
  public ComponentConfig[] getModuleComponents() {
    return myModuleComponents;
  }

  @Override
  public String getVendorEmail() {
    return myVendorEmail;
  }

  public void setVendorEmail( final String val )
  {
    myVendorEmail = val;
  }

  @Override
  public String getVendorUrl() {
    return myVendorUrl;
  }

  public void setVendorUrl( final String val )
  {
    myVendorUrl = val;
  }

  @Override
  public String getUrl() {
    return url;
  }

  public void setUrl( final String val )
  {
    url = val;
  }

  @NonNls
  public String toString() {
    return "PluginDescriptor[name='" + myName + "', classpath='" + myPath + "']";
  }

  public boolean isDeleted() {
    return myDeleted;
  }

  public void setDeleted(boolean deleted) {
    myDeleted = deleted;
  }

  public void setLoader(ClassLoader loader) {
    myLoader = loader;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IdeaPluginDescriptorImpl)) return false;

    final IdeaPluginDescriptorImpl pluginDescriptor = (IdeaPluginDescriptorImpl)o;

    return myName == null ? pluginDescriptor.myName == null : myName.equals(pluginDescriptor.myName);
  }

  public int hashCode() {
    return myName != null ? myName.hashCode() : 0;
  }

  @Override
  @NotNull
  public HelpSetPath[] getHelpSets() {
    return myHelpSets;
  }

  @Override
  public PluginId getPluginId() {
    return myId;
  }

  /*
     This setter was explicitly defined to be able to set downloads count for a
     descriptor outside its loading from the xml file since this information
     is available only from the site.
  */
  public void setDownloadsCount(String downloadsCount) {
    myDownloadCounter = downloadsCount;
  }

  @Override
  public String getDownloads(){
    return myDownloadCounter;
  }

  public long getDate(){
    return myDate;
  }

  /*
     This setter was explicitly defined to be able to set date for a
     descriptor outside its loading from the xml file since this information
     is available only from the site.
  */
  public void setDate( long date ){
    myDate = date;
  }

  @Override
  public ClassLoader getPluginClassLoader() {
    return myLoader != null ? myLoader : getClass().getClassLoader();
  }

  @Override
  public String getVendorLogoPath() {
    return myVendorLogoPath;
  }

  public void setVendorLogoPath(final String vendorLogoPath) {
    myVendorLogoPath = vendorLogoPath;
  }

  @Override
  public boolean getUseIdeaClassLoader() {
    return myUseIdeaClassLoader;
  }

  public boolean isUseCoreClassLoader() {
    return myUseCoreClassLoader;
  }

  public void setUseCoreClassLoader(final boolean useCoreClassLoader) {
    myUseCoreClassLoader = useCoreClassLoader;
  }

  private String computeDescription() {
    ResourceBundle bundle = null;
    if (myResourceBundleBaseName != null) {
      try {
        bundle = AbstractBundle.getResourceBundle(myResourceBundleBaseName, getPluginClassLoader());
      }
      catch (MissingResourceException e) {
        LOG.info("Cannot find plugin " + myId + " resource-bundle: " + myResourceBundleBaseName);
      }
    }

    if (bundle == null) {
      return myDescriptionChildText;
    }

    return CommonBundle.messageOrDefault(bundle, createDescriptionKey(myId), myDescriptionChildText == null ? "" : myDescriptionChildText);
  }

  public void insertDependency(@NotNull IdeaPluginDescriptor d) {
    PluginId[] deps = new PluginId[getDependentPluginIds().length + 1];
    deps[0] = d.getPluginId();
    System.arraycopy(myDependencies, 0, deps, 1, deps.length - 1);
    myDependencies = deps;
  }

  @Override
  public boolean isEnabled() {
    return myEnabled;
  }

  @Override
  public void setEnabled(final boolean enabled) {
    myEnabled = enabled;
  }

  @Override
  public String getSinceBuild() {
    return mySinceBuild;
  }

  @Override
  public String getUntilBuild() {
    return myUntilBuild;
  }

  Map<PluginId, List<String>> getOptionalConfigs() {
    return myOptionalConfigs;
  }

  Map<PluginId, List<IdeaPluginDescriptorImpl>> getOptionalDescriptors() {
    return myOptionalDescriptors;
  }

  void setOptionalDescriptors(@NotNull Map<PluginId, List<IdeaPluginDescriptorImpl>> optionalDescriptors) {
    myOptionalDescriptors = optionalDescriptors;
  }

  void mergeOptionalConfig(final IdeaPluginDescriptorImpl descriptor) {
    if (myExtensions == null) {
      myExtensions = descriptor.myExtensions;
    }
    else if (descriptor.myExtensions != null) {
      myExtensions.putAllValues(descriptor.myExtensions);
    }

    if (myExtensionsPoints == null) {
      myExtensionsPoints = descriptor.myExtensionsPoints;
    }
    else if (descriptor.myExtensionsPoints != null) {
      myExtensionsPoints.putAllValues(descriptor.myExtensionsPoints);
    }

    if (myActionElements == null) {
      myActionElements = descriptor.myActionElements;
    }
    else if (descriptor.myActionElements != null) {
      myActionElements.addAll(descriptor.myActionElements);
    }

    myAppComponents = mergeComponents(myAppComponents, descriptor.myAppComponents);
    myProjectComponents = mergeComponents(myProjectComponents, descriptor.myProjectComponents);
    myModuleComponents = mergeComponents(myModuleComponents, descriptor.myModuleComponents);
  }

  public Boolean getSkipped() {
    return mySkipped;
  }

  public void setSkipped(final Boolean skipped) {
    mySkipped = skipped;
  }

  @Override
  public boolean isBundled() {
    if (PluginManagerCore.CORE_PLUGIN_ID.equals(myId.getIdString())) {
      return true;
    }

    String path;
    try {
      //to avoid paths like this /home/kb/IDEA/bin/../config/plugins/APlugin
      path = getPath().getCanonicalPath();
    } catch (IOException e) {
      path = getPath().getAbsolutePath();
    }
    Application app = ApplicationManager.getApplication();
    if (app != null && app.isInternal()) {
      if (path.startsWith(PathManager.getHomePath() + File.separator + "out" + File.separator + "classes")) {
        return true;
      }
      if (app.isUnitTestMode() && !path.startsWith(PathManager.getPluginsPath() + File.separatorChar)) {
        return true;
      }
    }

    return path.startsWith(PathManager.getPreInstalledPluginsPath());
  }

  @Override
  public boolean allowBundledUpdate() {
    return myAllowBundledUpdate;
  }

  @Nullable
  public List<String> getModules() {
    return myModules;
  }
}
