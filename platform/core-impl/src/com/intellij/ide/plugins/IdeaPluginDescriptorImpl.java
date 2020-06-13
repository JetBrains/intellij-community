// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.AbstractBundle;
import com.intellij.DynamicBundle;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ref.GCWatcher;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApiStatus.Internal
public final class IdeaPluginDescriptorImpl implements IdeaPluginDescriptor {
  public enum OS {
    mac, linux, windows, unix, freebsd
  }

  public static final IdeaPluginDescriptorImpl[] EMPTY_ARRAY = new IdeaPluginDescriptorImpl[0];

  final Path path;
  // base path for resolving optional dependency descriptors
  final Path basePath;

  private final boolean myBundled;
  String myName;
  PluginId myId;
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
  String myUrl;
  @Nullable List<PluginDependency> pluginDependencies;
  @Nullable List<PluginId> incompatibilities;

  transient List<Path> jarFiles;

  private @Nullable List<Element> myActionElements;
  // extension point name -> list of extension elements
  // LinkedHashMap for predictable register order
  private @Nullable LinkedHashMap<String, List<Element>> epNameToExtensionElements;

  final ContainerDescriptor appContainerDescriptor = new ContainerDescriptor();
  final ContainerDescriptor projectContainerDescriptor = new ContainerDescriptor();
  final ContainerDescriptor moduleContainerDescriptor = new ContainerDescriptor();

  private List<PluginId> myModules;
  private ClassLoader myLoader;
  private String myDescriptionChildText;
  boolean myUseIdeaClassLoader;
  private boolean myUseCoreClassLoader;
  boolean myAllowBundledUpdate;
  boolean myImplementationDetail;
  boolean myRequireRestart;
  private String mySinceBuild;
  private String myUntilBuild;

  private boolean myEnabled = true;
  private boolean myDeleted;
  private boolean isExtensionsCleared;

  boolean incomplete;

  public IdeaPluginDescriptorImpl(@NotNull Path path, @NotNull Path basePath, boolean bundled) {
    this.path = path;
    this.basePath = basePath;
    myBundled = bundled;
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
                       @NotNull IdeaPluginDescriptorImpl mainDescriptor) {
    // root element always `!isIncludeElement`, and it means that result always is a singleton list
    // (also, plugin xml describes one plugin, this descriptor is not able to represent several plugins)
    if (JDOMUtil.isEmpty(element)) {
      markAsIncomplete(context, "Empty plugin descriptor", null);
      return false;
    }

    XmlReader.readIdAndName(this, element);

    if (myId != null && context.isPluginDisabled(myId)) {
      markAsIncomplete(context, null, null);
    }
    else {
      PathBasedJdomXIncluder.resolveNonXIncludeElement(element, basePath, context, pathResolver);
      if (myId == null || myName == null) {
        // read again after resolve
        XmlReader.readIdAndName(this, element);

        if (myId != null && context.isPluginDisabled(myId)) {
          markAsIncomplete(context, null, null);
        }
      }
    }

    if (incomplete) {
      myDescriptionChildText = element.getChildTextTrim("description");
      myCategory = element.getChildTextTrim("category");
      myVersion = element.getChildTextTrim("version");
      if (DescriptorListLoadingContext.LOG.isDebugEnabled()) {
        DescriptorListLoadingContext.LOG.debug("Skipping reading of " + myId + " from " + basePath + " (reason: disabled)");
      }
      List<Element> dependsElements = element.getChildren("depends");
      for (Element dependsElement : dependsElements) {
        readPluginDependency(basePath, context, dependsElement);
      }
      Element productElement = element.getChild("product-descriptor");
      if (productElement != null) {
        readProduct(context, productElement);
      }
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

    if (pluginDependencies != null) {
      XmlReader.readDependencies(mainDescriptor, this, context, pathResolver, pluginDependencies);
    }

    return true;
  }

  @TestOnly
  public void readForTest(@NotNull Element element) {
    doRead(element, DescriptorListLoadingContext.createSingleDescriptorContext(Collections.emptySet()), this);
  }

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
          XmlReader.readExtensionPoints(mainDescriptor, this, child);
          break;

        case "actions":
          if (myActionElements == null) {
            myActionElements = new ArrayList<>(child.getChildren());
          }
          else {
            myActionElements.addAll(child.getChildren());
          }
          clearContent = child.getAttributeValue("resource-bundle") == null;
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

        case "depends":
          if (!readPluginDependency(basePath, context, child)) {
            return true;
          }
          break;

        case "incompatible-with":
          readPluginIncompatibility(child);
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
          String value = StringUtil.nullize(child.getTextTrim());
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
          myVendor = StringUtil.nullize(child.getTextTrim());
          myVendorEmail = StringUtil.nullize(child.getAttributeValue("email"));
          myVendorUrl = StringUtil.nullize(child.getAttributeValue("url"));
          break;

        case "idea-version":
          mySinceBuild = StringUtil.nullize(child.getAttributeValue("since-build"));
          myUntilBuild = StringUtil.nullize(child.getAttributeValue("until-build"));
          if (!checkCompatibility(context)) {
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

  private void readProduct(@NotNull DescriptorListLoadingContext context, @NotNull Element child) {
    myProductCode = StringUtil.nullize(child.getAttributeValue("code"));
    myReleaseDate = parseReleaseDate(child.getAttributeValue("release-date"), context);
    myReleaseVersion = StringUtil.parseInt(child.getAttributeValue("release-version"), 0);
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
        markAsIncomplete(context, "Non-optional dependency plugin " + dependencyId + " is disabled", dependencyId);
      }

      isDisabledOrBroken = true;
    }
    else {
      if (context.result.isBroken(dependencyId)) {
        if (!isOptional) {
          DescriptorListLoadingContext.LOG.info("Skipping reading of " + myId + " from " + basePath + " (reason: non-optional dependency " + dependencyId + " is broken)");
          markAsIncomplete(context, "Non-optional dependency " + dependencyId + " is broken", null);
          return false;
        }

        isDisabledOrBroken = true;
      }
    }

    PluginDependency dependency = new PluginDependency(dependencyId, StringUtil.nullize(child.getAttributeValue("config-file")), isDisabledOrBroken);
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

  private boolean checkCompatibility(@NotNull DescriptorListLoadingContext context) {
    String since = mySinceBuild;
    String until = myUntilBuild;
    if (isBundled() || (since == null && until == null)) {
      return true;
    }

    String message = PluginManagerCore.getIncompatibleMessage(context.result.productBuildNumber.get(), since, until);
    if (message == null) {
      return true;
    }

    markAsIncomplete(context, null, null);  // error will be added by reportIncompatiblePlugin
    context.result.reportIncompatiblePlugin(this, message, since, until);
    return false;
  }

  @NotNull String formatErrorMessage(@NotNull String message) {
    String path = this.path.toString();
    StringBuilder builder = new StringBuilder();
    builder.append("The ").append(myName).append(" (id=").append(myId).append(", path=");
    builder.append(FileUtil.getLocationRelativeToUserHome(path, false));
    if (myVersion != null && !isBundled() && !myVersion.equals(PluginManagerCore.getBuildNumber().asString())) {
      builder.append(", version=").append(myVersion);
    }
    builder.append(") plugin ").append(message);
    return builder.toString();
  }

  private void markAsIncomplete(@NotNull DescriptorListLoadingContext context, @Nullable String errorMessage, @Nullable PluginId disabledDependency) {
    boolean wasIncomplete = incomplete;
    incomplete = true;
    setEnabled(false);
    if (myId != null && !wasIncomplete) {
      PluginError pluginError = errorMessage == null ? null : new PluginError(this, errorMessage, null, false);
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
    if (StringUtil.isEmpty(dateStr)) {
      return null;
    }

    try {
      return context.getDateParser().parse(dateStr);
    }
    catch (ParseException e) {
      DescriptorListLoadingContext.LOG.info("Error parse release date from plugin descriptor for plugin " + myName + " {" + myId + "}: " + e.getMessage());
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

  public @NotNull ContainerDescriptor getAppContainerDescriptor() {
    return appContainerDescriptor;
  }

  @ApiStatus.Internal
  public void registerExtensions(@NotNull ExtensionsAreaImpl area,
                                 @NotNull IdeaPluginDescriptorImpl rootDescriptor,
                                 @NotNull ContainerDescriptor containerDescriptor,
                                 @Nullable List<Runnable> listenerCallbacks) {
    Map<String, List<Element>> extensions = containerDescriptor.extensions;
    if (extensions != null) {
      area.registerExtensions(extensions, rootDescriptor, listenerCallbacks);
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
      if (!area.registerExtensions(entry.getKey(), entry.getValue(), rootDescriptor, listenerCallbacks)) {
        iterator.remove();
        if (other == null) {
          other = new LinkedHashMap<>();
        }
        addExtensionList(other, entry.getKey(), entry.getValue());
      }
    }
    isExtensionsCleared = true;

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
    String result = myDescription;
    if (result != null) {
      return result;
    }

    ResourceBundle bundle = null;
    if (myResourceBundleBaseName != null) {
      try {
        bundle = DynamicBundle.INSTANCE.getResourceBundle(myResourceBundleBaseName, getPluginClassLoader());
      }
      catch (MissingResourceException e) {
        PluginManagerCore.getLogger().info("Cannot find plugin " + myId + " resource-bundle: " + myResourceBundleBaseName);
      }
    }

    if (bundle == null) {
      result = myDescriptionChildText;
    }
    else {
      result = AbstractBundle.messageOrDefault(bundle, "plugin." + myId + ".description", StringUtil.notNullize(myDescriptionChildText));
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

  public @Nullable Map<String, List<Element>> getExtensions() {
    if (isExtensionsCleared) {
      throw new IllegalStateException("Trying to retrieve extensions list after extension elements have been cleared");
    }
    if (epNameToExtensionElements == null) {
      return null;
    }
    else {
      return new LinkedHashMap<>(epNameToExtensionElements);
    }
  }

  /**
   * @deprecated Do not use. If you want to get class loader for own plugin, just use your current class's class loader.
   */
  @Deprecated
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

  @NotNull List<Path> collectClassPath(@NotNull Map<String, String[]> additionalLayoutMap) {
    if (!Files.isDirectory(path)) {
      return Collections.singletonList(path);
    }

    List<Path> result = new ArrayList<>();
    Path classesDir = path.resolve("classes");
    if (Files.exists(classesDir)) {
      result.add(classesDir);
    }

    if (PluginManagerCore.usePluginClassLoader) {
      Path productionDirectory = path.getParent();
      if (productionDirectory.endsWith("production")) {
        result.add(path);
        String moduleName = path.getFileName().toString();
        String[] additionalPaths = additionalLayoutMap.get(moduleName);
        if (additionalPaths != null) {
          for (String path : additionalPaths) {
            result.add(productionDirectory.resolve(path));
          }
        }
      }
    }

    try (DirectoryStream<Path> childStream = Files.newDirectoryStream(path.resolve("lib"))) {
      for (Path f : childStream) {
        if (Files.isRegularFile(f)) {
          String name = f.getFileName().toString();
          if (StringUtilRt.endsWithIgnoreCase(name, ".jar") || StringUtilRt.endsWithIgnoreCase(name, ".zip")) {
            result.add(f);
          }
        }
        else {
          result.add(f);
        }
      }
    }
    catch (NoSuchFileException ignore) {
    }
    catch (IOException e) {
      PluginManagerCore.getLogger().debug(e);
    }
    return result;
  }

  public @Nullable List<Element> getActionDescriptionElements() {
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

  public boolean unloadClassLoader(int timeoutMs) {
    if (timeoutMs == 0) {
      myLoader = null;
      return true;
    }

    GCWatcher watcher = GCWatcher.tracking(myLoader);
    myLoader = null;
    return watcher.tryCollect(timeoutMs);
  }

  @Override
  public PluginId getPluginId() {
    return myId;
  }

  @Override
  public ClassLoader getPluginClassLoader() {
    return myLoader != null ? myLoader : getClass().getClassLoader();
  }

  public boolean isUseIdeaClassLoader() {
    return myUseIdeaClassLoader;
  }

  boolean isUseCoreClassLoader() {
    return myUseCoreClassLoader;
  }

  void setUseCoreClassLoader() {
    myUseCoreClassLoader = true;
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
    if (epNameToExtensionElements == null) {
      epNameToExtensionElements = descriptor.epNameToExtensionElements;
    }
    else if (descriptor.epNameToExtensionElements != null) {
      descriptor.epNameToExtensionElements.forEach((name, list) -> {
        addExtensionList(epNameToExtensionElements, name, list);
      });
    }

    if (myActionElements == null) {
      myActionElements = descriptor.myActionElements;
    }
    else if (descriptor.myActionElements != null) {
      myActionElements.addAll(descriptor.myActionElements);
    }

    appContainerDescriptor.merge(descriptor.appContainerDescriptor);
    projectContainerDescriptor.merge(descriptor.projectContainerDescriptor);
    moduleContainerDescriptor.merge(descriptor.moduleContainerDescriptor);
  }

  private static void addExtensionList(@NotNull Map<String, List<Element>> map, @NotNull String name, @NotNull List<Element> list) {
    List<Element> mapList = map.computeIfAbsent(name, __ -> list);
    if (mapList != list) {
      mapList.addAll(list);
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

  @Override
  public boolean isRequireRestart() {
    return myRequireRestart;
  }

  public @NotNull List<PluginId> getModules() {
    return myModules == null ? Collections.emptyList() : myModules;
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
    return "PluginDescriptor(name=" + myName + ", id=" + myId + ", path=" + path + ", version=" + myVersion + ")";
  }
}