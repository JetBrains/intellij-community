// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.AbstractBundle;
import com.intellij.CommonBundle;
import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.ServiceDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SafeJdomFactory;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.Interner;
import com.intellij.util.messages.ListenerDescriptor;
import com.intellij.util.ref.GCWatcher;
import gnu.trove.THashMap;
import org.jdom.Attribute;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IdeaPluginDescriptorImpl implements IdeaPluginDescriptor {
  public enum OS {
    mac, linux, windows, unix, freebsd
  }

  public static final IdeaPluginDescriptorImpl[] EMPTY_ARRAY = new IdeaPluginDescriptorImpl[0];

  private static final Logger LOG = Logger.getInstance(IdeaPluginDescriptorImpl.class);

  private static final String APPLICATION_SERVICE = "com.intellij.applicationService";
  private static final String PROJECT_SERVICE = "com.intellij.projectService";
  private static final String MODULE_SERVICE = "com.intellij.moduleService";

  static final List<String> SERVICE_QUALIFIED_ELEMENT_NAMES = Arrays.asList(APPLICATION_SERVICE, PROJECT_SERVICE, MODULE_SERVICE);

  private final Path myPath;
  private final boolean myBundled;
  private String myName;
  private PluginId myId;
  private volatile String myDescription;
  private @Nullable String myProductCode;
  private @Nullable Date myReleaseDate;
  private int myReleaseVersion;
  private String myResourceBundleBaseName;
  private String myChangeNotes;
  private String myVersion;
  private String myVendor;
  private String myVendorEmail;
  private String myVendorUrl;
  private String myCategory;
  private String myUrl;
  private PluginId[] myDependencies = PluginId.EMPTY_ARRAY;
  private PluginId[] myOptionalDependencies = PluginId.EMPTY_ARRAY;

  // used only during initializing
  transient Map<PluginId, List<Map.Entry<String, IdeaPluginDescriptorImpl>>> optionalConfigs;

  private @Nullable List<Element> myActionElements;
  // extension point name -> list of extension elements
  private @Nullable THashMap<String, List<Element>> myExtensions;

  private final ContainerDescriptor myAppContainerDescriptor = new ContainerDescriptor();
  private final ContainerDescriptor myProjectContainerDescriptor = new ContainerDescriptor();
  private final ContainerDescriptor myModuleContainerDescriptor = new ContainerDescriptor();

  private List<PluginId> myModules;
  private ClassLoader myLoader;
  private String myDescriptionChildText;
  private boolean myUseIdeaClassLoader;
  private boolean myUseCoreClassLoader;
  private boolean myAllowBundledUpdate;
  private boolean myImplementationDetail;
  private String mySinceBuild;
  private String myUntilBuild;

  private boolean myEnabled = true;
  private boolean myDeleted;
  private boolean myExtensionsCleared = false;

  public IdeaPluginDescriptorImpl(@NotNull Path pluginPath, boolean bundled) {
    myPath = pluginPath;
    myBundled = bundled;
  }

  @NotNull
  @ApiStatus.Internal
  public ContainerDescriptor getApp() {
    return myAppContainerDescriptor;
  }

  @NotNull
  @ApiStatus.Internal
  public ContainerDescriptor getProject() {
    return myProjectContainerDescriptor;
  }

  @NotNull
  @ApiStatus.Internal
  public ContainerDescriptor getModule() {
    return myModuleContainerDescriptor;
  }

  @Override
  public File getPath() {
    return myPath.toFile();
  }

  @NotNull
  public Path getPluginPath() {
    return myPath;
  }

  /**
   * @deprecated Use {@link PluginManager#loadDescriptorFromFile(IdeaPluginDescriptorImpl, Path, SafeJdomFactory, boolean, Set)}
   */
  @Deprecated
  public void loadFromFile(@NotNull File file, @Nullable SafeJdomFactory factory, boolean ignoreMissingInclude)
    throws IOException, JDOMException {
    PluginManager.loadDescriptorFromFile(this, file.toPath(), factory, ignoreMissingInclude, PluginManagerCore.disabledPlugins());
  }

  public void readExternal(@NotNull Element element,
                           @Nullable Path basePath,
                           boolean ignoreMissingInclude,
                           @NotNull PathBasedJdomXIncluder.PathResolver<?> pathResolver,
                           @NotNull DescriptorLoadingContext loadingContext) {
    // root element always `!isIncludeElement` and it means that result always is a singleton list
    // (also, plugin xml describes one plugin, this descriptor is not able to represent several plugins)
    if (JDOMUtil.isEmpty(element)) {
      return;
    }

    XmlReader.readIdAndName(this, element);

    if (myId == null || !loadingContext.parentContext.disabledPlugins.contains(myId)) {
      PathBasedJdomXIncluder.resolveNonXIncludeElement(element, basePath, ignoreMissingInclude, pathResolver);
      if (myId == null || myName == null) {
        // read again after resolve
        XmlReader.readIdAndName(this, element);
      }
    }
    else if (LOG.isDebugEnabled()) {
      LOG.debug("Skipping resolving of " + myId + " from " + basePath);
    }

    XmlReader.readMetaInfo(this, element);

    List<PluginDependency> dependencies = null;
    for (Content content : element.getContent()) {
      if (!(content instanceof Element)) {
        continue;
      }

      Element child = (Element)content;
      switch (child.getName()) {
        case "extensions":
          XmlReader.readExtensions(this, loadingContext.parentContext.getStringInterner(), child);
          break;

        case "extensionPoints":
          XmlReader.readExtensionPoints(this, child);
          break;

        case "actions":
          if (myActionElements == null) {
            myActionElements = new ArrayList<>(child.getChildren());
          }
          else {
            myActionElements.addAll(child.getChildren());
          }
          break;

        case "module":
          String moduleName = child.getAttributeValue("value");
          if (moduleName != null) {
            if (myModules == null) {
              myModules = Collections.singletonList(PluginId.getId(moduleName));
            }
            else {
              if (myModules.size() == 1) {
                List<PluginId> singleton = myModules;
                myModules = new ArrayList<>(4);
                myModules.addAll(singleton);
              }
              myModules.add(PluginId.getId(moduleName));
            }
          }
          break;

        case "application-components":
          // because of x-pointer, maybe several application-components tag in document
          readComponents(child, myAppContainerDescriptor);
          break;

        case "project-components":
          readComponents(child, myProjectContainerDescriptor);
          break;

        case "module-components":
          readComponents(child, myModuleContainerDescriptor);
          break;

        case "applicationListeners":
          XmlReader.readListeners(this, child, myAppContainerDescriptor);
          break;

        case "projectListeners":
          XmlReader.readListeners(this, child, myProjectContainerDescriptor);
          break;

        case "depends":
          String pluginId = child.getTextTrim();
          if (!pluginId.isEmpty()) {
            if (dependencies == null) {
              dependencies = new ArrayList<>();
            }

            PluginDependency dependency = new PluginDependency();
            dependency.pluginId = PluginId.getId(pluginId);
            dependency.optional = Boolean.parseBoolean(child.getAttributeValue("optional"));
            dependency.configFile = StringUtil.nullize(child.getAttributeValue("config-file"));
            dependencies.add(dependency);
          }
          break;

        case "category":
          myCategory = StringUtil.nullize(child.getTextTrim());
          break;

        case "change-notes":
          myChangeNotes = StringUtil.nullize(child.getTextTrim());
          break;

        case "version":
          myVersion = StringUtil.nullize(child.getTextTrim());
          break;

        case "description":
          myDescriptionChildText = StringUtil.nullize(child.getTextTrim());
          break;

        case "resource-bundle":
          myResourceBundleBaseName = StringUtil.nullize(child.getTextTrim());
          break;

        case "product-descriptor":
          myProductCode = StringUtil.nullize(child.getAttributeValue("code"));
          myReleaseDate = parseReleaseDate(child.getAttributeValue("release-date"), loadingContext.parentContext);
          myReleaseVersion = StringUtil.parseInt(child.getAttributeValue("release-version"), 0);
          break;

        case "vendor":
          myVendor = StringUtil.nullize(child.getChildTextTrim("name"));
          myVendorEmail = StringUtil.nullize(child.getAttributeValue("email"));
          myVendorUrl = StringUtil.nullize(child.getAttributeValue("url"));
          break;

        case "idea-version":
          mySinceBuild = StringUtil.nullize(child.getAttributeValue("since-build"));
          myUntilBuild = StringUtil.nullize(child.getAttributeValue("until-build"));
          break;
      }

      child.getContent().clear();
    }

    if (myVersion == null) {
      myVersion = loadingContext.parentContext.getDefaultVersion();
    }

    if (dependencies != null) {
      XmlReader.readDependencies(this, dependencies);
    }
  }

  @NotNull
  private static ServiceDescriptor readServiceDescriptor(@NotNull Element element) {
    ServiceDescriptor descriptor = new ServiceDescriptor();
    descriptor.serviceInterface = element.getAttributeValue("serviceInterface");
    descriptor.serviceImplementation = StringUtil.nullize(element.getAttributeValue("serviceImplementation"));
    descriptor.testServiceImplementation = StringUtil.nullize(element.getAttributeValue("testServiceImplementation"));
    descriptor.headlessImplementation = StringUtil.nullize(element.getAttributeValue("headlessImplementation"));
    descriptor.configurationSchemaKey = element.getAttributeValue("configurationSchemaKey");

    String preload = element.getAttributeValue("preload");
    if (preload != null) {
      if (preload.equals("true")) {
        descriptor.preload = ServiceDescriptor.PreloadMode.TRUE;
      }
      else if (preload.equals("await")) {
        descriptor.preload = ServiceDescriptor.PreloadMode.AWAIT;
      }
      else if (preload.equals("notHeadless")) {
        descriptor.preload = ServiceDescriptor.PreloadMode.NOT_HEADLESS;
      }
      else {
        LOG.error("Unknown preload mode value: " + JDOMUtil.writeElement(element));
      }
    }

    descriptor.overrides = Boolean.parseBoolean(element.getAttributeValue("overrides"));
    return descriptor;
  }

  private static void readComponents(@NotNull Element parent, @NotNull ContainerDescriptor containerDescriptor) {
    List<Content> content = parent.getContent();
    int contentSize = content.size();
    if (contentSize == 0) {
      return;
    }

    List<ComponentConfig> result = containerDescriptor.getComponentListToAdd(contentSize);
    for (Content child : content) {
      if (!(child instanceof Element)) {
        continue;
      }

      Element componentElement = (Element)child;
      if (!componentElement.getName().equals("component")) {
        continue;
      }

      ComponentConfig componentConfig = new ComponentConfig();
      Map<String, String> options = null;
      loop:
      for (Element elementChild : componentElement.getChildren()) {
        switch (elementChild.getName()) {
          case "skipForDefaultProject":
            if (!readBoolValue(elementChild.getTextTrim())) {
              componentConfig.setLoadForDefaultProject(true);
            }
            break;

          case "loadForDefaultProject":
            componentConfig.setLoadForDefaultProject(readBoolValue(elementChild.getTextTrim()));
            break;

          case "interface-class":
            componentConfig.setInterfaceClass(elementChild.getTextTrim());
            break;

          case "implementation-class":
            componentConfig.setImplementationClass(elementChild.getTextTrim());
            break;

          case "headless-implementation-class":
            componentConfig.setHeadlessImplementationClass(elementChild.getTextTrim());
            break;

          case "option":
            String name = elementChild.getAttributeValue("name");
            String value = elementChild.getAttributeValue("value");
            if (name != null) {
              if (name.equals("os")) {
                if (!isComponentSuitableForOs(value)) {
                  continue loop;
                }
              }
              else {
                if (options == null) {
                  options = Collections.singletonMap(name, value);
                }
                else {
                  if (options.size() == 1) {
                    options = new HashMap<>(options);
                  }
                  options.put(name, value);
                }
              }
            }
            break;
        }
      }

      if (options != null) {
        componentConfig.options = options;
      }

      result.add(componentConfig);
    }
  }

  private static boolean readBoolValue(@NotNull String value) {
    return value.isEmpty() || value.equalsIgnoreCase("true");
  }

  @Nullable
  private Date parseReleaseDate(@Nullable String dateStr, @NotNull DescriptorListLoadingContext context) {
    if (StringUtil.isEmpty(dateStr)) {
      return null;
    }

    try {
      return context.getDateParser().parse(dateStr);
    }
    catch (ParseException e) {
      LOG.info("Error parse release date from plugin descriptor for plugin " + myName + " {" + myId + "}: " + e.getMessage());
    }
    return null;
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

  void registerExtensionPoints(@NotNull ExtensionsAreaImpl area, @NotNull ComponentManager componentManager) {
    ContainerDescriptor containerDescriptor;
    if (componentManager.getPicoContainer().getParent() == null) {
      containerDescriptor = myAppContainerDescriptor;
    }
    else if (componentManager.getPicoContainer().getParent().getParent() == null) {
      containerDescriptor = myProjectContainerDescriptor;
    }
    else {
      containerDescriptor = myModuleContainerDescriptor;
    }

    List<Element> extensionsPoints = containerDescriptor.extensionsPoints;
    if (extensionsPoints != null) {
      area.registerExtensionPoints(this, extensionsPoints, componentManager);
    }
  }

  @Nullable
  private ContainerDescriptor getContainerDescriptorByExtensionArea(@Nullable String area) {
    if (area == null) {
      return myAppContainerDescriptor;
    }
    else if ("IDEA_PROJECT".equals(area)) {
      return myProjectContainerDescriptor;
    }
    else if ("IDEA_MODULE".equals(area)) {
      return myModuleContainerDescriptor;
    }
    else {
      return null;
    }
  }

  @NotNull
  public ContainerDescriptor getAppContainerDescriptor() {
    return myAppContainerDescriptor;
  }

  @NotNull
  public ContainerDescriptor getProjectContainerDescriptor() {
    return myProjectContainerDescriptor;
  }

  @NotNull
  public ContainerDescriptor getModuleContainerDescriptor() {
    return myModuleContainerDescriptor;
  }

  @ApiStatus.Internal
  public void registerExtensions(@NotNull ExtensionsAreaImpl area, @NotNull ComponentManager componentManager, boolean notifyListeners) {
    THashMap<String, List<Element>> extensions;
    if (componentManager.getPicoContainer().getParent() == null) {
      extensions = myAppContainerDescriptor.extensions;
      if (extensions == null) {
        if (myExtensions == null) {
          return;
        }

        myExtensions.retainEntries((name, list) -> {
          if (area.registerExtensions(name, list, this, componentManager, notifyListeners)) {
            if (myAppContainerDescriptor.extensions == null) {
              myAppContainerDescriptor.extensions = new THashMap<>();
            }
            addExtensionList(myAppContainerDescriptor.extensions, name, list);
            return false;
          }
          return true;
        });
        myExtensionsCleared = true;

        if (myExtensions.isEmpty()) {
          myExtensions = null;
        }

        return;
      }
      // else... it means that another application is created for the same set of plugins - at least, this case should be supported for tests
    }
    else {
      extensions = myExtensions;
      if (extensions == null) {
        return;
      }
    }

    extensions.forEachEntry((name, list) -> {
      area.registerExtensions(name, list, this, componentManager, notifyListeners);
      return true;
    });
  }

  @Override
  public String getDescription() {
    String result = myDescription;
    if (result != null) {
      return result;
    }

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
      result = myDescriptionChildText;
    }
    else {
      result = CommonBundle.messageOrDefault(bundle, "plugin." + myId + ".description", StringUtil.notNullize(myDescriptionChildText));
    }
    myDescription = result;
    return result;
  }

  @Override
  public String getChangeNotes() {
    return myChangeNotes;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Nullable
  @Override
  public String getProductCode() {
    return myProductCode;
  }

  @Nullable
  @Override
  public Date getReleaseDate() {
    return myReleaseDate;
  }

  @Override
  public int getReleaseVersion() {
    return myReleaseVersion;
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

  /*
     This setter was explicitly defined to be able to set a category for a
     descriptor outside its loading from the xml file.
     Problem was that most commonly plugin authors do not publish the plugin's
     category in its .xml file so to be consistent in plugins representation
     (e.g. in the Plugins form) we have to set this value outside.
  */
  public void setCategory(String category) {
    myCategory = category;
  }

  @Nullable
  public Map<String, List<Element>> getExtensions() {
    if (myExtensionsCleared) {
      throw new IllegalStateException("Trying to retrieve extensions list after extension elements have been cleared");
    }
    if (myExtensions == null) {
      return null;
    }
    else {
      Map<String, List<Element>> result = new THashMap<>(myExtensions.size());
      result.putAll(myExtensions);
      return result;
    }
  }

  /**
   * @deprecated Do not use. If you want to get class loader for own plugin, just use your current class's class loader.
   */
  @NotNull
  @Deprecated
  public List<File> getClassPath() {
    File path = myPath.toFile();
    if (!path.isDirectory()) {
      return Collections.singletonList(path);
    }

    List<File> result = new ArrayList<>();
    File classesDir = new File(path, "classes");
    if (classesDir.exists()) {
      result.add(classesDir);
    }

    File[] files = new File(path, "lib").listFiles();
    if (files == null || files.length <= 0) {
      return result;
    }

    for (File f : files) {
      if (f.isFile()) {
        String name = f.getName();
        if (StringUtil.endsWithIgnoreCase(name, ".jar") || StringUtil.endsWithIgnoreCase(name, ".zip")) {
          result.add(f);
        }
      }
      else {
        result.add(f);
      }
    }
    return result;
  }

  @NotNull
  List<Path> collectClassPath() {
    if (!Files.isDirectory(myPath)) {
      return Collections.singletonList(myPath);
    }

    List<Path> result = new ArrayList<>();
    Path classesDir = myPath.resolve("classes");
    if (Files.exists(classesDir)) {
      result.add(classesDir);
    }

    try (DirectoryStream<Path> childStream = Files.newDirectoryStream(myPath.resolve("lib"))) {
      for (Path f : childStream) {
        if (Files.isRegularFile(f)) {
          String name = f.getFileName().toString();
          if (StringUtil.endsWithIgnoreCase(name, ".jar") || StringUtil.endsWithIgnoreCase(name, ".zip")) {
            result.add(f);
          }
        }
        else {
          result.add(f);
        }
      }
    }
    catch (FileNotFoundException ignore) {
    }
    catch (IOException e) {
      LOG.debug(e);
    }
    return result;
  }

  @Override
  @Nullable
  public List<Element> getActionDescriptionElements() {
    return myActionElements;
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
    return myUrl;
  }

  public void setUrl(String val) {
    myUrl = val;
  }

  public boolean isDeleted() {
    return myDeleted;
  }

  public void setDeleted(boolean deleted) {
    myDeleted = deleted;
  }

  public void setLoader(@Nullable ClassLoader loader) {
    myLoader = loader;
  }

  public boolean unloadClassLoader() {
    GCWatcher watcher = GCWatcher.tracking(myLoader);
    myLoader = null;
    return watcher.tryCollect();
  }

  @Override
  public PluginId getPluginId() {
    return myId;
  }

  @Override
  public ClassLoader getPluginClassLoader() {
    return myLoader != null ? myLoader : getClass().getClassLoader();
  }

  public boolean getUseIdeaClassLoader() {
    return myUseIdeaClassLoader;
  }

  boolean isUseCoreClassLoader() {
    return myUseCoreClassLoader;
  }

  void setUseCoreClassLoader(@SuppressWarnings("SameParameterValue") boolean useCoreClassLoader) {
    myUseCoreClassLoader = useCoreClassLoader;
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

  void mergeOptionalConfig(@NotNull IdeaPluginDescriptorImpl descriptor) {
    if (myExtensions == null) {
      myExtensions = descriptor.myExtensions;
    }
    else if (descriptor.myExtensions != null) {
      descriptor.myExtensions.forEachEntry((name, list) -> {
        addExtensionList(myExtensions, name, list);
        return true;
      });
    }

    if (myActionElements == null) {
      myActionElements = descriptor.myActionElements;
    }
    else if (descriptor.myActionElements != null) {
      myActionElements.addAll(descriptor.myActionElements);
    }

    myAppContainerDescriptor.merge(descriptor.myAppContainerDescriptor);
    myProjectContainerDescriptor.merge(descriptor.myProjectContainerDescriptor);
    myModuleContainerDescriptor.merge(descriptor.myModuleContainerDescriptor);
  }

  private static void addExtensionList(@NotNull Map<String, List<Element>> map, @NotNull String name, @NotNull List<Element> list) {
    List<Element> myList = map.get(name);
    if (myList == null) {
      map.put(name, list);
    }
    else {
      myList.addAll(list);
    }
  }

  @Override
  public boolean isBundled() {
    return myBundled;
  }

  @Override
  public boolean allowBundledUpdate() {
    return myAllowBundledUpdate;
  }

  @Override
  public boolean isImplementationDetail() {
    return myImplementationDetail;
  }

  @NotNull
  public List<PluginId> getModules() {
    return ContainerUtil.notNullize(myModules);
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof IdeaPluginDescriptorImpl && myId == ((IdeaPluginDescriptorImpl)o).myId;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myId);
  }

  @Override
  public String toString() {
    return "PluginDescriptor(name=" + myName + ", id=" + myId + ", path=" + myPath + ")";
  }

  private static boolean isComponentSuitableForOs(@Nullable String os) {
    if (StringUtil.isEmpty(os)) {
      return true;
    }

    if (os.equals(OS.mac.name())) {
      return SystemInfo.isMac;
    }
    else if (os.equals(OS.linux.name())) {
      return SystemInfo.isLinux;
    }
    else if (os.equals(OS.windows.name())) {
      return SystemInfo.isWindows;
    }
    else if (os.equals(OS.unix.name())) {
      return SystemInfo.isUnix;
    }
    else if (os.equals(OS.freebsd.name())) {
      return SystemInfo.isFreeBSD;
    }
    else {
      throw new IllegalArgumentException("Unknown OS '" + os + "'");
    }
  }

  private static final class PluginDependency {
    public PluginId pluginId;
    public boolean optional;
    public String configFile;
  }

  private static final class XmlReader {
    static void readListeners(@NotNull IdeaPluginDescriptorImpl descriptor, @NotNull Element list, @NotNull ContainerDescriptor containerDescriptor) {
      List<Content> content = list.getContent();
      List<ListenerDescriptor> result = containerDescriptor.listeners;
      if (result == null) {
        result = new ArrayList<>(content.size());
        containerDescriptor.listeners = result;
      }
      else {
        ((ArrayList<ListenerDescriptor>)result).ensureCapacity(result.size() + content.size());
      }

      for (Content item : content) {
        if (!(item instanceof Element)) {
          continue;
        }

        Element child = (Element)item;
        String listenerClassName = child.getAttributeValue("class");
        String topicClassName = child.getAttributeValue("topic");
        if (listenerClassName == null || topicClassName == null) {
          LOG.error("Listener descriptor is not correct: " + JDOMUtil.writeElement(child));
        }
        else {
          result.add(new ListenerDescriptor(listenerClassName, topicClassName,
                                            getBoolean("activeInTestMode", child), getBoolean("activeInHeadlessMode", child), descriptor));
        }
      }
    }

    static void readIdAndName(@NotNull IdeaPluginDescriptorImpl descriptor, @NotNull Element element) {
      String idString = element.getChildTextTrim("id");
      String name = element.getChildTextTrim("name");
      if (idString == null) {
        idString = name;
      }
      else if (name == null) {
        name = idString;
      }

      descriptor.myName = name;
      descriptor.myId = StringUtil.isEmpty(idString) ? null : PluginId.getId(idString);
    }

    static void readMetaInfo(@NotNull IdeaPluginDescriptorImpl descriptor, @NotNull Element element) {
      if (!element.hasAttributes()) {
        return;
      }

      List<Attribute> attributes = element.getAttributes();
      for (Attribute attribute : attributes) {
        switch (attribute.getName()) {
          case "url":
            descriptor.myUrl = StringUtil.nullize(attribute.getValue());
            break;

          case "use-idea-classloader":
            descriptor.myUseIdeaClassLoader = Boolean.parseBoolean(attribute.getValue());
            break;

          case "allow-bundled-update":
            descriptor.myAllowBundledUpdate = Boolean.parseBoolean(attribute.getValue());
            break;

          case "implementation-detail":
            descriptor.myImplementationDetail = Boolean.parseBoolean(attribute.getValue());
            break;

          case "version":
            String internalVersionString = StringUtil.nullize(attribute.getValue());
            if (internalVersionString != null) {
              try {
                Integer.parseInt(internalVersionString);
              }
              catch (NumberFormatException e) {
                LOG.error(new PluginException("Invalid value in plugin.xml format version: '" + internalVersionString + "'", e, descriptor.myId));
              }
            }
            break;
        }
      }
    }

    static void readDependencies(@NotNull IdeaPluginDescriptorImpl descriptor, @NotNull List<PluginDependency> dependencies) {
      // https://youtrack.jetbrains.com/issue/IDEA-206274
      int size = 0;
      for (int i = 0, n = dependencies.size(); i < n; i++) {
        PluginDependency dependency = dependencies.get(i);
        size++;
        if (!dependency.optional) {
          continue;
        }

        for (int j = 0; j < i; j++) {
          PluginDependency prev = dependencies.get(j);
          if (!prev.optional && prev.pluginId == dependency.pluginId) {
            dependency.optional = false;
            dependencies.set(j, null);
            size--;
            break;
          }
        }
      }

      PluginId[] dependentPlugins = new PluginId[size];
      int optionalSize = 0;
      int index = 0;
      for (PluginDependency dependency : dependencies) {
        if (dependency == null) {
          continue;
        }

        PluginId id = dependency.pluginId;
        dependentPlugins[index++] = dependency.pluginId;

        // because of https://youtrack.jetbrains.com/issue/IDEA-206274, configFile maybe not only for optional dependencies
        if (dependency.configFile != null) {
          if (descriptor.optionalConfigs == null) {
            descriptor.optionalConfigs = new LinkedHashMap<>();
          }
          ContainerUtilRt.putValue(id, new AbstractMap.SimpleEntry<>(dependency.configFile, null), descriptor.optionalConfigs);
        }

        if (dependency.optional) {
          optionalSize++;
        }
      }

      descriptor.myDependencies = dependentPlugins;

      if (optionalSize > 0) {
        if (optionalSize == dependentPlugins.length) {
          descriptor.myOptionalDependencies = dependentPlugins;
        }
        else {
          PluginId[] optionalDependencies = new PluginId[optionalSize];
          index = 0;
          for (PluginDependency dependency : dependencies) {
            if (dependency.optional) {
              optionalDependencies[index++] = dependency.pluginId;
            }
          }
          descriptor.myOptionalDependencies = optionalDependencies;
        }
      }
    }

    private static boolean getBoolean(@NotNull String name, @NotNull Element child) {
      String value = child.getAttributeValue(name);
      return value == null || Boolean.parseBoolean(value);
    }


    static void readExtensions(@NotNull IdeaPluginDescriptorImpl descriptor, @NotNull Interner<String> stringInterner, Element child) {
      String ns = child.getAttributeValue("defaultExtensionNs");
      THashMap<String, List<Element>> epNameToExtensions = descriptor.myExtensions;
      for (Element extensionElement : child.getChildren()) {
        String os = extensionElement.getAttributeValue("os");
        if (os != null) {
          extensionElement.removeAttribute("os");
          if (!isComponentSuitableForOs(os)) {
            continue;
          }
        }

        String qualifiedExtensionPointName = stringInterner.intern(ExtensionsAreaImpl.extractPointName(extensionElement, ns));
        ContainerDescriptor containerDescriptor;
        if (qualifiedExtensionPointName.equals(APPLICATION_SERVICE)) {
          containerDescriptor = descriptor.myAppContainerDescriptor;
        }
        else if (qualifiedExtensionPointName.equals(PROJECT_SERVICE)) {
          containerDescriptor = descriptor.myProjectContainerDescriptor;
        }
        else if (qualifiedExtensionPointName.equals(MODULE_SERVICE)) {
          containerDescriptor = descriptor.myModuleContainerDescriptor;
        }
        else {
          if (epNameToExtensions == null) {
            epNameToExtensions = new THashMap<>();
            descriptor.myExtensions = epNameToExtensions;
          }

          List<Element> list = epNameToExtensions.get(qualifiedExtensionPointName);
          if (list == null) {
            list = new SmartList<>();
            epNameToExtensions.put(qualifiedExtensionPointName, list);
          }
          list.add(extensionElement);
          continue;
        }

        containerDescriptor.addService(readServiceDescriptor(extensionElement));
      }
    }

    static void readExtensionPoints(@NotNull IdeaPluginDescriptorImpl descriptor, @NotNull Element child) {
      for (Element extensionPoint : child.getChildren()) {
        String area = extensionPoint.getAttributeValue(ExtensionsAreaImpl.ATTRIBUTE_AREA);
        ContainerDescriptor containerDescriptor = descriptor.getContainerDescriptorByExtensionArea(area);
        if (containerDescriptor == null) {
          LOG.error("Unknown area: " + area);
          continue;
        }

        List<Element> result = containerDescriptor.extensionsPoints;
        if (result == null) {
          result = new ArrayList<>();
          containerDescriptor.extensionsPoints = result;
        }
        result.add(extensionPoint);
      }
    }
  }
}