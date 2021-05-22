// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.AbstractBundle;
import com.intellij.DynamicBundle;
import com.intellij.core.CoreBundle;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.util.text.Strings;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApiStatus.Internal
public final class IdeaPluginDescriptorImpl implements IdeaPluginDescriptor {
  public enum OS {
    mac, linux, windows, unix, freebsd
  }

  public static final IdeaPluginDescriptorImpl[] EMPTY_ARRAY = new IdeaPluginDescriptorImpl[0];
  public static final Pattern EXPLICIT_BIG_NUMBER_PATTERN = Pattern.compile("(.*)\\.(9{4,}+|10{4,}+)");

  final Path path;
  // base path for resolving optional dependency descriptors
  final Path basePath;

  private final boolean isBundled;

  String name;
  PluginId id;

  // only for sub descriptors
  String descriptorPath;

  private volatile String myDescription;
  private @Nullable String myProductCode;
  private @Nullable Date myReleaseDate;
  private int myReleaseVersion;
  private boolean myIsLicenseOptional;
  private String myResourceBundleBaseName;
  private String myChangeNotes;
  private String myVersion;
  private String myVendor;
  private String myVendorEmail;
  private String myVendorUrl;
  private String myCategory;
  String url;
  @Nullable List<PluginDependency> pluginDependencies;
  @Nullable List<PluginId> incompatibilities;

  transient List<Path> jarFiles;

  private @Nullable List<Element> actionElements;
  // extension point name -> list of extension elements
  private @Nullable Map<String, List<Element>> epNameToExtensionElements;

  final ContainerDescriptor appContainerDescriptor = new ContainerDescriptor();
  final ContainerDescriptor projectContainerDescriptor = new ContainerDescriptor();
  final ContainerDescriptor moduleContainerDescriptor = new ContainerDescriptor();

  @NotNull PluginContentDescriptor contentDescriptor = PluginContentDescriptor.EMPTY;
  @NotNull ModuleDependenciesDescriptor dependencyDescriptor = ModuleDependenciesDescriptor.EMPTY;

  private List<PluginId> modules;
  private ClassLoader classLoader;
  private @NlsSafe String descriptionChildText;
  boolean useIdeaClassLoader;
  private boolean useCoreClassLoader;
  boolean allowBundledUpdate;
  boolean implementationDetail;
  boolean requireRestart;
  String packagePrefix;
  private String mySinceBuild;
  private String myUntilBuild;

  private boolean isEnabled = true;
  private boolean isDeleted;

  boolean incomplete;

  public IdeaPluginDescriptorImpl(@NotNull Path path, @NotNull Path basePath, boolean isBundled) {
    this.path = path;
    this.basePath = basePath;
    this.isBundled = isBundled;
  }

  @Override
  public @Nullable String getDescriptorPath() {
    return descriptorPath;
  }

  @ApiStatus.Internal
  public @NotNull ContainerDescriptor getApp() {
    return appContainerDescriptor;
  }

  @ApiStatus.Internal
  public @NotNull ContainerDescriptor getProject() {
    return projectContainerDescriptor;
  }

  @ApiStatus.Internal
  public @NotNull ContainerDescriptor getModule() {
    return moduleContainerDescriptor;
  }

  @Override
  public @NotNull List<IdeaPluginDependency> getDependencies() {
    return pluginDependencies == null ? Collections.emptyList() : Collections.unmodifiableList(pluginDependencies);
  }

  @ApiStatus.Internal
  public @NotNull List<PluginDependency> getPluginDependencies() {
    return pluginDependencies == null ? Collections.emptyList() : pluginDependencies;
  }

  @Override
  public @NotNull Path getPluginPath() {
    return path;
  }

  boolean readExternal(@NotNull Element element,
                       @NotNull PathBasedJdomXIncluder.PathResolver<?> pathResolver,
                       @NotNull DescriptorListLoadingContext context,
                       @NotNull IdeaPluginDescriptorImpl mainDescriptor) throws IOException {
    // root element always `!isIncludeElement`, and it means that result always is a singleton list
    // (also, plugin xml describes one plugin, this descriptor is not able to represent several plugins)
    if (JDOMUtil.isEmpty(element)) {
      markAsIncomplete(context, () -> CoreBundle.message("plugin.loading.error.descriptor.file.is.empty"), null);
      return false;
    }

    XmlReader.readIdAndName(this, element);

    //some information required for "incomplete" plugins can be in included files
    PathBasedJdomXIncluder.resolveNonXIncludeElement(element, basePath, context, pathResolver);
    if (id != null && context.isPluginDisabled(id)) {
      markAsIncomplete(context, null, null);
    }
    else {
      if (id == null || name == null) {
        // read again after resolve
        XmlReader.readIdAndName(this, element);

        if (id != null && context.isPluginDisabled(id)) {
          markAsIncomplete(context, null, null);
        }
      }
    }

    if (incomplete) {
      readEssentialPluginInformation(element, context);
      return false;
    }

    XmlReader.readMetaInfo(this, element);

    pluginDependencies = null;
    if (doRead(element, context, mainDescriptor)) {
      return false;
    }

    if (myVersion == null) {
      myVersion = context.getDefaultVersion();
    }
    if (myVendor == null) {
      myVendor = mainDescriptor.myVendor;
    }
    if (myResourceBundleBaseName == null) {
      myResourceBundleBaseName = mainDescriptor.myResourceBundleBaseName;
    }

    if (pluginDependencies != null) {
      XmlReader.readDependencies(mainDescriptor, this, context, pathResolver, pluginDependencies);
    }

    // include module file descriptor if not specified as `depends` (old way - xi:include)
    if (this == mainDescriptor) {
      moduleLoop: for (PluginContentDescriptor.ModuleItem module : contentDescriptor.modules) {
        String descriptorFile = module.name + ".xml";
        if (pluginDependencies != null) {
          for (PluginDependency dependency : pluginDependencies) {
            if (descriptorFile.equals(dependency.configFile)) {
              // ok, it is specified in old way as depends tag - skip it
              continue moduleLoop;
            }
          }
        }

        // inject as xi:include does
        try {
          Element moduleElement = pathResolver.resolvePath(basePath, descriptorFile, context.getXmlFactory());
          doRead(moduleElement, context, mainDescriptor);
        }
        catch (JDOMException e) {
          throw new RuntimeException(e);
        }

        module.isInjected = true;
      }
    }

    return true;
  }

  private void readEssentialPluginInformation(@NotNull Element element, @NotNull DescriptorListLoadingContext context) {
    if (descriptionChildText == null) {
      descriptionChildText = element.getChildTextTrim("description");
    }
    if (myCategory == null) {
      myCategory = element.getChildTextTrim("category");
    }
    if (myVersion == null) {
      myVersion = element.getChildTextTrim("version");
    }
    if (DescriptorListLoadingContext.LOG.isDebugEnabled()) {
      DescriptorListLoadingContext.LOG.debug("Skipping reading of " + id + " from " + basePath + " (reason: disabled)");
    }
    if (pluginDependencies == null) {
      List<Element> dependsElements = element.getChildren("depends");
      for (Element dependsElement : dependsElements) {
        readPluginDependency(basePath, context, dependsElement);
      }
    }
    Element productElement = element.getChild("product-descriptor");
    if (productElement != null) {
      readProduct(context, productElement);
    }
    if (modules == null) {
      List<Element> moduleElements = element.getChildren("module");
      for (Element moduleElement : moduleElements) {
        readModule(moduleElement);
      }
    }
  }

  @TestOnly
  public void readForTest(@NotNull Element element) {
    id = PluginManagerCore.CORE_ID;
    doRead(element, DescriptorListLoadingContext.createSingleDescriptorContext(Collections.emptySet()), this);
  }

  /**
   * @return {@code true} - if there are compatibility problems with IDE (`depends`, `since-until`).
   * <br>{@code false} - otherwise
   */
  private boolean doRead(@NotNull Element element,
                        @NotNull DescriptorListLoadingContext context,
                        @NotNull IdeaPluginDescriptorImpl mainDescriptor) {
    for (Content content : element.getContent()) {
      if (!(content instanceof Element)) {
        continue;
      }

      boolean clearContent = true;
      Element child = (Element)content;
      switch (child.getName()) {
        case "extensions":
          epNameToExtensionElements = XmlReader.readExtensions(this, epNameToExtensionElements, context, child);
          break;

        case "extensionPoints":
          XmlReader.readExtensionPoints(this, child);
          break;

        case "actions":
          if (actionElements == null) {
            actionElements = new ArrayList<>(child.getChildren());
          }
          else {
            actionElements.addAll(child.getChildren());
          }
          clearContent = child.getAttributeValue("resource-bundle") == null;
          break;

        case "module":
          readModule(child);
          break;

        case "application-components":
          // because of x-pointer, maybe several application-components tag in document
          readComponents(child, appContainerDescriptor);
          break;

        case "project-components":
          readComponents(child, projectContainerDescriptor);
          break;

        case "module-components":
          readComponents(child, moduleContainerDescriptor);
          break;

        case "applicationListeners":
          XmlReader.readListeners(child, appContainerDescriptor, mainDescriptor);
          break;

        case "projectListeners":
          XmlReader.readListeners(child, projectContainerDescriptor, mainDescriptor);
          break;

        case "content":
          XmlReader.readContent(child, this);
          break;

        case "dependencies":
          XmlReader.readNewDependencies(child, this);
          break;

        case "depends":
          if (!readPluginDependency(basePath, context, child)) {
            return true;
          }
          break;

        case "incompatible-with":
          readPluginIncompatibility(child);
          break;

        case "category":
          myCategory = Strings.nullize(child.getTextTrim());
          break;

        case "change-notes":
          myChangeNotes = Strings.nullize(child.getTextTrim());
          break;

        case "version":
          myVersion = Strings.nullize(child.getTextTrim());
          break;

        case "description":
          descriptionChildText = Strings.nullize(child.getTextTrim());
          break;

        case "resource-bundle":
          String value = Strings.nullize(child.getTextTrim());
          if (PluginManagerCore.CORE_ID.equals(mainDescriptor.getPluginId())) {
            DescriptorListLoadingContext.LOG.warn("<resource-bundle>" + value + "</resource-bundle> tag is found in an xml descriptor included into the platform part of the IDE " +
                                                  "but the platform part uses predefined bundles (e.g. ActionsBundle for actions) anyway; " +
                                                  "this tag must be replaced by a corresponding attribute in some inner tags (e.g. by 'resource-bundle' attribute in 'actions' tag)");
          }
          if (myResourceBundleBaseName != null && !Objects.equals(myResourceBundleBaseName, value)) {
            DescriptorListLoadingContext.LOG.warn("Resource bundle redefinition for plugin '" + mainDescriptor.getPluginId() + "'. " +
                     "Old value: " + myResourceBundleBaseName + ", new value: " + value);
          }
          myResourceBundleBaseName = value;
          break;

        case "product-descriptor":
          readProduct(context, child);
          break;

        case "vendor":
          myVendor = Strings.nullize(child.getTextTrim());
          myVendorEmail = Strings.nullize(child.getAttributeValue("email"));
          myVendorUrl = Strings.nullize(child.getAttributeValue("url"));
          break;

        case "idea-version":
          mySinceBuild = Strings.nullize(child.getAttributeValue("since-build"));
          myUntilBuild = Strings.nullize(child.getAttributeValue("until-build"));
          if (!checkCompatibility(context, () -> readEssentialPluginInformation(element, context))) {
            return true;
          }
          break;
      }

      if (clearContent) {
        child.getContent().clear();
      }
    }
    return false;
  }

  private void readModule(Element child) {
    String moduleName = child.getAttributeValue("value");
    if (moduleName == null) {
      return;
    }

    if (modules == null) {
      modules = Collections.singletonList(PluginId.getId(moduleName));
    }
    else {
      if (modules.size() == 1) {
        List<PluginId> singleton = modules;
        modules = new ArrayList<>(4);
        modules.addAll(singleton);
      }
      modules.add(PluginId.getId(moduleName));
    }
  }

  private void readProduct(@NotNull DescriptorListLoadingContext context, @NotNull Element child) {
    myProductCode = Strings.nullize(child.getAttributeValue("code"));
    myReleaseDate = parseReleaseDate(child.getAttributeValue("release-date"), context);
    myReleaseVersion = StringUtilRt.parseInt(child.getAttributeValue("release-version"), 0);
    myIsLicenseOptional = Boolean.parseBoolean(child.getAttributeValue("optional", "false"));
  }

  private void readPluginIncompatibility(@NotNull Element child) {
    String pluginId = child.getTextTrim();
    if (pluginId.isEmpty()) return;

    if (incompatibilities == null) {
      incompatibilities = new ArrayList<>();
    }
    incompatibilities.add(PluginId.getId(pluginId));
  }

  private boolean readPluginDependency(@NotNull Path basePath, @NotNull DescriptorListLoadingContext context, @NotNull Element child) {
    String dependencyIdString = child.getTextTrim();
    if (dependencyIdString.isEmpty()) {
      return true;
    }

    PluginId dependencyId = PluginId.getId(dependencyIdString);
    boolean isOptional = Boolean.parseBoolean(child.getAttributeValue("optional"));
    boolean isDisabledOrBroken = false;
    // context.isPluginIncomplete must be not checked here as another version of plugin maybe supplied later from another source
    if (context.isPluginDisabled(dependencyId)) {
      if (!isOptional) {
        markAsIncomplete(context, () -> {
          return CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", dependencyId);
        }, dependencyId);
      }

      isDisabledOrBroken = true;
    }
    else if (context.result.isBroken(dependencyId)) {
      if (!isOptional) {
        DescriptorListLoadingContext.LOG.info("Skipping reading of " +
                                              id + " from " + basePath + " (reason: non-optional dependency " + dependencyId + " is broken)");
        markAsIncomplete(context, () -> {
          return CoreBundle.message("plugin.loading.error.short.depends.on.broken.plugin", dependencyId);
        }, null);
        return false;
      }

      isDisabledOrBroken = true;
    }

    PluginDependency dependency = new PluginDependency(dependencyId, Strings.nullize(child.getAttributeValue("config-file")), isDisabledOrBroken);
    dependency.isOptional = isOptional;
    if (pluginDependencies == null) {
      pluginDependencies = new ArrayList<>();
    }
    else {
      // https://youtrack.jetbrains.com/issue/IDEA-206274
      for (PluginDependency item : pluginDependencies) {
        if (item.id == dependencyId) {
          if (item.isOptional) {
            if (!isOptional) {
              item.isOptional = false;
            }
          }
          else {
            dependency.isOptional = false;
            if (item.configFile == null) {
              item.configFile = dependency.configFile;
              return true;
            }
          }
        }
      }
    }
    pluginDependencies.add(dependency);
    return true;
  }

  private boolean checkCompatibility(@NotNull DescriptorListLoadingContext context, Runnable beforeCreateErrorCallback) {
    String since = mySinceBuild;
    String until = myUntilBuild;
    if (isBundled() || (since == null && until == null)) {
      return true;
    }

    @Nullable PluginLoadingError error = PluginManagerCore.checkBuildNumberCompatibility(this, context.result.productBuildNumber.get(),
                                                                                         beforeCreateErrorCallback);
    if (error == null) {
      return true;
    }

    markAsIncomplete(context, null, null);  // error will be added by reportIncompatiblePlugin
    context.result.reportIncompatiblePlugin(this, error);
    return false;
  }

  private void markAsIncomplete(@NotNull DescriptorListLoadingContext context, @Nullable Supplier<@Nls String> shortMessage, @Nullable PluginId disabledDependency) {
    boolean wasIncomplete = incomplete;
    incomplete = true;
    setEnabled(false);
    if (id != null && !wasIncomplete) {
      PluginLoadingError pluginError = shortMessage == null ? null : PluginLoadingError.createWithoutNotification(this, shortMessage);
      if (pluginError != null && disabledDependency != null) {
        pluginError.setDisabledDependency(disabledDependency);
      }
      context.result.addIncompletePlugin(this, pluginError);
    }
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
                if (value != null && !XmlReader.isSuitableForOs(value)) {
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

  private @Nullable Date parseReleaseDate(@Nullable String dateStr, @NotNull DescriptorListLoadingContext context) {
    if (Strings.isEmpty(dateStr)) {
      return null;
    }

    try {
      return context.getDateParser().parse(dateStr);
    }
    catch (ParseException e) {
      DescriptorListLoadingContext.LOG.info("Error parse release date from plugin descriptor for plugin " + name + " {" + id + "}: " + e.getMessage());
    }
    return null;
  }

  /**
   * Convert build number like '146.9999' to '146.*' (like plugin repository does) to ensure that plugins which have such values in
   * 'until-build' attribute will be compatible with 146.SNAPSHOT build.
   */
  public static String convertExplicitBigNumberInUntilBuildToStar(@Nullable String build) {
    if (build == null) {
      return null;
    }
    Matcher matcher = EXPLICIT_BIG_NUMBER_PATTERN.matcher(build);
    if (matcher.matches()) {
      return matcher.group(1) + ".*";
    }
    return build;
  }

  public @NotNull ContainerDescriptor getAppContainerDescriptor() {
    return appContainerDescriptor;
  }

  @ApiStatus.Internal
  public void registerExtensions(@NotNull ExtensionsAreaImpl area,
                                 @NotNull ContainerDescriptor containerDescriptor,
                                 @Nullable List<? super Runnable> listenerCallbacks) {
    Map<String, List<Element>> extensions = containerDescriptor.extensions;
    if (extensions != null) {
      area.registerExtensions(extensions, this, listenerCallbacks);
      return;
    }

    if (epNameToExtensionElements == null) {
      return;
    }

    // app container: in most cases will be only app-level extensions - to reduce map copying, assume that all extensions are app-level and then filter out
    // project container: rest of extensions wil be mostly project level
    // module container: just use rest, area will not register unrelated extension anyway as no registered point
    containerDescriptor.extensions = epNameToExtensionElements;

    LinkedHashMap<String, List<Element>> other = null;
    Iterator<Map.Entry<String, List<Element>>> iterator = containerDescriptor.extensions.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, List<Element>> entry = iterator.next();
      if (!area.registerExtensions(entry.getKey(), entry.getValue(), this, listenerCallbacks)) {
        iterator.remove();
        if (other == null) {
          other = new LinkedHashMap<>();
        }
        addExtensionList(other, entry.getKey(), entry.getValue());
      }
    }

    if (containerDescriptor.extensions.isEmpty()) {
      containerDescriptor.extensions = Collections.emptyMap();
    }

    if (containerDescriptor == projectContainerDescriptor) {
      // assign unsorted to module level to avoid concurrent access during parallel module loading
      moduleContainerDescriptor.extensions = other;
      epNameToExtensionElements = null;
    }
    else {
      epNameToExtensionElements = other;
    }
  }

  @Override
  public String getDescription() {
    @NlsSafe String result = myDescription;
    if (result != null) {
      return result;
    }

    ResourceBundle bundle = null;
    if (myResourceBundleBaseName != null) {
      try {
        bundle = DynamicBundle.INSTANCE.getResourceBundle(myResourceBundleBaseName, getPluginClassLoader());
      }
      catch (MissingResourceException e) {
        PluginManagerCore.getLogger().info("Cannot find plugin " + id + " resource-bundle: " + myResourceBundleBaseName);
      }
    }

    if (bundle == null) {
      result = descriptionChildText;
    }
    else {
      result = AbstractBundle.messageOrDefault(bundle, "plugin." + id + ".description", Strings.notNullize(descriptionChildText));
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
    return name;
  }

  @Override
  public @Nullable String getProductCode() {
    return myProductCode;
  }

  @Override
  public @Nullable Date getReleaseDate() {
    return myReleaseDate;
  }

  @Override
  public int getReleaseVersion() {
    return myReleaseVersion;
  }

  @Override
  public boolean isLicenseOptional() {
    return myIsLicenseOptional;
  }

  @SuppressWarnings("deprecation")
  @Override
  public PluginId @NotNull [] getDependentPluginIds() {
    if (pluginDependencies == null || pluginDependencies.isEmpty()) {
      return PluginId.EMPTY_ARRAY;
    }
    int size = pluginDependencies.size();
    PluginId[] result = new PluginId[size];
    for (int i = 0; i < size; i++) {
      result[i] = pluginDependencies.get(i).id;
    }
    return result;
  }

  @Override
  public PluginId @NotNull [] getOptionalDependentPluginIds() {
    if (pluginDependencies == null || pluginDependencies.isEmpty()) {
      return PluginId.EMPTY_ARRAY;
    }
    return pluginDependencies.stream().filter(it -> it.isOptional).map(it -> it.id).toArray(PluginId[]::new);
  }

  @Override
  public String getVendor() {
    return myVendor;
  }

  @Override
  public String getOrganization() {
    //TODO[ivan.chirkov]: support organizations for installed plugins
    return "";
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

  public @NotNull Map<String, List<Element>> getUnsortedEpNameToExtensionElements() {
    return epNameToExtensionElements == null ? Collections.emptyMap() : Collections.unmodifiableMap(epNameToExtensionElements);
  }

  /**
   * @deprecated Do not use. If you want to get class loader for own plugin, just use your current class's class loader.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public @NotNull List<File> getClassPath() {
    File path = this.path.toFile();
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
        if (Strings.endsWithIgnoreCase(name, ".jar") || Strings.endsWithIgnoreCase(name, ".zip")) {
          result.add(f);
        }
      }
      else {
        result.add(f);
      }
    }
    return result;
  }

  public @Nullable List<Element> getActionDescriptionElements() {
    return actionElements;
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

  public void setUrl(String val) {
    url = val;
  }

  public boolean isDeleted() {
    return isDeleted;
  }

  public void setDeleted(boolean deleted) {
    isDeleted = deleted;
  }

  @Nullable ClassLoader getClassLoader() {
    return classLoader;
  }

  void setClassLoader(@Nullable ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  @Override
  public PluginId getPluginId() {
    return id;
  }

  @Override
  public @NotNull ClassLoader getPluginClassLoader() {
    return classLoader == null ? getClass().getClassLoader() : classLoader;
  }

  public boolean isUseIdeaClassLoader() {
    return useIdeaClassLoader;
  }

  boolean isUseCoreClassLoader() {
    return useCoreClassLoader;
  }

  void setUseCoreClassLoader() {
    useCoreClassLoader = true;
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void setEnabled(final boolean enabled) {
    isEnabled = enabled;
  }

  @Override
  public String getSinceBuild() {
    return mySinceBuild;
  }

  @Override
  public String getUntilBuild() {
    return myUntilBuild;
  }

  private static void addExtensionList(@NotNull Map<String, List<Element>> map, @NotNull String name, @NotNull List<Element> list) {
    List<Element> mapList = map.computeIfAbsent(name, __ -> list);
    if (mapList != list) {
      mapList.addAll(list);
    }
  }

  @Override
  public boolean isBundled() {
    return isBundled;
  }

  @Override
  public boolean allowBundledUpdate() {
    return allowBundledUpdate;
  }

  @Override
  public boolean isImplementationDetail() {
    return implementationDetail;
  }

  @Override
  public boolean isRequireRestart() {
    return requireRestart;
  }

  public @NotNull List<PluginId> getModules() {
    return modules == null ? Collections.emptyList() : modules;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof IdeaPluginDescriptorImpl && id == ((IdeaPluginDescriptorImpl)o).id;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  @Override
  public String toString() {
    // don't expose user home in error messages
    String pathString = path.toString().replace(System.getProperty("user.home") + File.separatorChar, "~" + File.separatorChar);
    return "PluginDescriptor(name=" + name +
           ", id=" + id +
           ", descriptorPath=" + (descriptorPath == null ? "plugin.xml" : descriptorPath) +
           ", path=" + pathString +
           ", version=" + myVersion +
           ", package=" + packagePrefix +
           ")";
  }
}