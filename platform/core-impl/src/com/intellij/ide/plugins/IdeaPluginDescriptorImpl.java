/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.StringInterner;
import com.intellij.util.xmlb.JDOMXIncluder;
import com.intellij.util.xmlb.XmlSerializer;
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

/**
 * @author mike
 */
public class IdeaPluginDescriptorImpl implements IdeaPluginDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.plugins.PluginDescriptor");

  public static final IdeaPluginDescriptorImpl[] EMPTY_ARRAY = new IdeaPluginDescriptorImpl[0];
  private String myName;
  private PluginId myId;
  private final NullableLazyValue<String> myDescription = new NullableLazyValue<String>() {
    @Override
    protected String compute() {
      return computeDescription();
    }
  };
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
  private Map<PluginId, String> myOptionalConfigs;
  private Map<PluginId, IdeaPluginDescriptorImpl> myOptionalDescriptors;
  @Nullable private List<Element> myActionsElements;
  private ComponentConfig[] myAppComponents = null;
  private ComponentConfig[] myProjectComponents = null;
  private ComponentConfig[] myModuleComponents = null;
  private boolean myDeleted = false;
  private ClassLoader myLoader;
  private HelpSetPath[] myHelpSets;
  @Nullable private MultiMap<String, Element> myExtensions;
  @Nullable private MultiMap<String, Element> myExtensionsPoints;
  private String myDescriptionChildText;
  private String myDownloadCounter;
  private long myDate;
  private boolean myUseIdeaClassLoader;
  private boolean myUseCoreClassLoader;
  private boolean myEnabled = true;

  private String mySinceBuild;
  private String myUntilBuild;

  private Boolean mySkipped;

  private List<String> myModules = null;

  public IdeaPluginDescriptorImpl(@NotNull File pluginPath) {
    myPath = pluginPath;
  }

  IdeaPluginDescriptorImpl() {
  }

  public void setPath(@NotNull File path) {
    myPath = path;
  }

  @Override
  public File getPath() {
    return myPath;
  }

  private static final StringInterner ourInterner = new StringInterner();

  @NotNull
  public static String intern(@NotNull String s) {
    synchronized (ourInterner) {
      return ourInterner.intern(s);
    }
  }

  public static void internJDOMElement(@NotNull Element rootElement) {
    JDOMUtil.internElement(rootElement, ourInterner);
  }

  public void readExternal(@NotNull Document document, @NotNull URL url) throws InvalidDataException, FileNotFoundException {
    document = JDOMXIncluder.resolve(document, url.toExternalForm());
    Element rootElement = document.getRootElement();
    internJDOMElement(rootElement);
    readExternal(document.getRootElement());
  }

  public void readExternal(@NotNull URL url) throws InvalidDataException, FileNotFoundException {
    try {
      Document document = JDOMUtil.loadDocument(url);
      readExternal(document, url);
    }
    catch (FileNotFoundException e) {
      throw e;
    }
    catch (IOException e) {
      throw new InvalidDataException(e);
    }
    catch (JDOMException e) {
      throw new InvalidDataException(e);
    }
  }

  private void readExternal(@NotNull Element element) {
    final PluginBean pluginBean = XmlSerializer.deserialize(element, PluginBean.class);

    url = pluginBean.url;
    myName = pluginBean.name;
    String idString = pluginBean.id;
    if (idString == null || idString.isEmpty()) {
      idString = myName;
    }
    myId = PluginId.getId(idString);

    String internalVersionString = pluginBean.formatVersion;
    if (internalVersionString != null) {
      try {
        Integer.parseInt(internalVersionString);
      }
      catch (NumberFormatException e) {
        LOG.error(new PluginException("Invalid value in plugin.xml format version: '" + internalVersionString+"'", e, myId));
      }
    }
    myUseIdeaClassLoader = pluginBean.useIdeaClassLoader;
    if (pluginBean.ideaVersion != null) {
      mySinceBuild = pluginBean.ideaVersion.sinceBuild;
      myUntilBuild = pluginBean.ideaVersion.untilBuild;
    }

    myResourceBundleBaseName = pluginBean.resourceBundle;

    myDescriptionChildText = pluginBean.description;
    myChangeNotes = pluginBean.changeNotes;
    myVersion = pluginBean.pluginVersion;
    myCategory = pluginBean.category;


    if (pluginBean.vendor != null) {
      myVendor = pluginBean.vendor.name;
      myVendorEmail = pluginBean.vendor.email;
      myVendorUrl = pluginBean.vendor.url;
      myVendorLogoPath = pluginBean.vendor.logo;
    }

    // preserve items order as specified in xml (filterBadPlugins will not fail if module comes first)
    Set<PluginId> dependentPlugins = new LinkedHashSet<PluginId>();
    Set<PluginId> optionalDependentPlugins = new LinkedHashSet<PluginId>();
    myOptionalConfigs = new HashMap<PluginId, String>();
    if (pluginBean.dependencies != null) {
      for (PluginDependency dependency : pluginBean.dependencies) {
        String text = dependency.pluginId;
        if (text != null && !text.isEmpty()) {
          final PluginId id = PluginId.getId(text);
          dependentPlugins.add(id);
          if (dependency.optional) {
            optionalDependentPlugins.add(id);
            if (dependency.configFile != null && !dependency.configFile.isEmpty()) {
              myOptionalConfigs.put(id, dependency.configFile);
            }
          }
        }
      }
    }
    myDependencies = dependentPlugins.isEmpty() ? PluginId.EMPTY_ARRAY : dependentPlugins.toArray(new PluginId[dependentPlugins.size()]);
    myOptionalDependencies = optionalDependentPlugins.isEmpty() ? PluginId.EMPTY_ARRAY : optionalDependentPlugins.toArray(new PluginId[optionalDependentPlugins.size()]);

    List<HelpSetPath> hsPaths = new ArrayList<HelpSetPath>();
    if (pluginBean.helpSets != null) {
      for (PluginHelpSet pluginHelpSet : pluginBean.helpSets) {
        HelpSetPath hsPath = new HelpSetPath(pluginHelpSet.file, pluginHelpSet.path);
        hsPaths.add(hsPath);
      }
    }
    myHelpSets = hsPaths.isEmpty() ? HelpSetPath.EMPTY : hsPaths.toArray(new HelpSetPath[hsPaths.size()]);

    myAppComponents = pluginBean.applicationComponents;
    myProjectComponents = pluginBean.projectComponents;
    myModuleComponents = pluginBean.moduleComponents;

    if (myAppComponents == null) myAppComponents = ComponentConfig.EMPTY_ARRAY;
    if (myProjectComponents == null) myProjectComponents = ComponentConfig.EMPTY_ARRAY;
    if (myModuleComponents == null) myModuleComponents = ComponentConfig.EMPTY_ARRAY;

    List<Element> extensions = copyElements(pluginBean.extensions);
    if (extensions != null) {
      myExtensions = new MultiMap<String, Element>();
      for (Element extension : extensions) {
        myExtensions.putValue(ExtensionsAreaImpl.extractEPName(extension), extension);
      }
    }
    
    List<Element> extensionPoints = copyElements(pluginBean.extensionPoints);
    if (extensionPoints != null) {
      myExtensionsPoints = new MultiMap<String, Element>();
      for (Element extensionPoint : extensionPoints) {
        myExtensionsPoints.putValue(extensionPoint.getAttributeValue(ExtensionsAreaImpl.ATTRIBUTE_AREA), extensionPoint);
      }
    }
    
    myActionsElements = copyElements(pluginBean.actions);

    if (pluginBean.modules != null && !pluginBean.modules.isEmpty()) {
      myModules = pluginBean.modules;
    }
  }

  @Nullable
  private static List<Element> copyElements(final Element[] elements) {
    if (elements != null) {
      List<Element> result = new ArrayList<Element>();
      for (Element extensionsRoot : elements) {
        for (final Object o : extensionsRoot.getChildren()) {
          Element element = (Element)o;
          internJDOMElement(element);
          result.add(element);
        }
      }
      return result;
    }
    return null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String createDescriptionKey(final PluginId id) {
    return "plugin." + id + ".description";
  }

  void registerExtensionPoints(ExtensionsArea area) {
    if (myExtensionsPoints != null) {
      for (Element element : myExtensionsPoints.get(area.getAreaClass())) {
        area.registerExtensionPoint(this, element);
      }
    }
  }

  void registerExtensions(ExtensionsArea area, String epName) {
    if (myExtensions != null) {
      for (Element element : myExtensions.get(epName)) {
        area.registerExtension(this, element);
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

  @SuppressWarnings({"HardCodedStringLiteral"})
  @NotNull
  public List<File> getClassPath() {
    if (myPath.isDirectory()) {
      final List<File> result = new ArrayList<File>();
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
    return myActionsElements;
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

  @Override
  public String getVendorUrl() {
    return myVendorUrl;
  }

  @Override
  public String getUrl() {
    return url;
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
     This setter was explicitly defined to be able to set a category for a
     descriptor outside its loading from the xml file.
     Problem was that most commonly plugin authors do not publish the plugin's
     category in its .xml file so to be consistent in plugins representation
     (e.g. in the Plugins form) we have to set this value outside.
  */
  public void setCategory( String category ){
    myCategory = category;
  }

  /*
     This setter was explicitly defined to be able to set downloads count for a
     descriptor outside its loading from the xml file since this information
     is available only from the site.
  */
  public void setDownloadsCount( String dwnlds ){
    myDownloadCounter = dwnlds;
  }

  @Override
  public String getDownloads(){
    return myDownloadCounter;
  }

  /*
     This setter was explicitly defined to be able to set date for a
     descriptor outside its loading from the xml file since this information
     is available only from the site.
  */
  public void setDate( long date ){
    myDate = date;
  }

  public long getDate(){
    return myDate;
  }

  public void setVendor( final String val )
  {
    myVendor = val;
  }
  public void setVendorEmail( final String val )
  {
    myVendorEmail = val;
  }
  public void setVendorUrl( final String val )
  {
    myVendorUrl = val;
  }
  public void setUrl( final String val )
  {
    url = val;
  }

  @Override
  public ClassLoader getPluginClassLoader() {
    return myLoader != null ? myLoader : getClass().getClassLoader();
  }

  @Override
  public String getVendorLogoPath() {
    return myVendorLogoPath;
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

  public void setVendorLogoPath(final String vendorLogoPath) {
    myVendorLogoPath = vendorLogoPath;
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

  public void insertDependency(final IdeaPluginDescriptor d) {
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

  Map<PluginId, String> getOptionalConfigs() {
    return myOptionalConfigs;
  }

  Map<PluginId, IdeaPluginDescriptorImpl> getOptionalDescriptors() {
    return myOptionalDescriptors;
  }

  void setOptionalDescriptors(final Map<PluginId, IdeaPluginDescriptorImpl> optionalDescriptors) {
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

    if (myActionsElements == null) {
      myActionsElements = descriptor.myActionsElements;
    }
    else if (descriptor.myActionsElements != null) {
      myActionsElements.addAll(descriptor.myActionsElements);
    }

    myAppComponents = mergeComponents(myAppComponents, descriptor.myAppComponents);
    myProjectComponents = mergeComponents(myProjectComponents, descriptor.myProjectComponents);
    myModuleComponents = mergeComponents(myModuleComponents, descriptor.myModuleComponents);
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

  public Boolean getSkipped() {
    return mySkipped;
  }

  public void setSkipped(final Boolean skipped) {
    mySkipped = skipped;
  }

  @Override
  public boolean isBundled() {
    return getPath().getAbsolutePath().startsWith(PathManager.getPreInstalledPluginsPath());
  }

  @Nullable
  public List<String> getModules() {
    return myModules;
  }
}
