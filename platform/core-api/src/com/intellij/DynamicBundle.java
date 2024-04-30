// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.DefaultBundleService;
import com.intellij.util.LocalizationUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.*;
import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

public class DynamicBundle extends AbstractBundle {
  private static final Logger LOG = Logger.getInstance(DynamicBundle.class);

  //TODO: this is a temporary solutions, should be done better in IJPL-148813
  private static @NotNull String ourLangTag = System.getProperty("intellij.searchableOptions.i18n.locale", Locale.ENGLISH.toLanguageTag());

  private static final ConcurrentMap<String, ResourceBundle> bundles = CollectionFactory.createConcurrentWeakValueMap();
  private boolean isUpdateNeeded;
  /**
   * Creates a new instance of the message bundle. It's usually stored in a private static final field, and static methods delegating
   * to its {@link #getMessage} and {@link #getLazyMessage} methods are added.
   *
   * @param bundleClass  any class from the module containing the bundle, it's used to locate the file with the messages
   * @param pathToBundle qualified name of the file with the messages (without the extension, with slashes replaced by dots)
   */
  public DynamicBundle(@NotNull Class<?> bundleClass, @NotNull String pathToBundle) {
    super(bundleClass, pathToBundle);
    isUpdateNeeded = LocalizationUtil.INSTANCE.isL10nPluginInitialized();
  }

  /**
   * <h3>Obsolescence notice</h3>
   * <p>
   * It's better to prefer delegation to inheritance, and use {@link #DynamicBundle(Class, String)} instead.
   * </p>
   * <p/>
   * Use this constructor in bundle classes which inherit from this class.
   */
  @Obsolete
  protected DynamicBundle(@NotNull String pathToBundle) {
    super(pathToBundle);
    isUpdateNeeded = LocalizationUtil.INSTANCE.isL10nPluginInitialized();
  }

  // see BundleUtil
  @Override
  protected @NotNull ResourceBundle findBundle(@NotNull String pathToBundle,
                                               @NotNull ClassLoader baseLoader,
                                               @NotNull ResourceBundle.Control control) {
    return resolveResourceBundle(getBundleClassLoader(), baseLoader, pathToBundle, getLocale(),
                                 (loader, locale) -> super.findBundle(pathToBundle, loader, control, locale));
  }

  private static @NotNull ResourceBundle resolveResourceBundle(@NotNull ClassLoader bundleClassLoader,
                                                               @NotNull ClassLoader baseLoader,
                                                               @NotNull String defaultPath,
                                                               @NotNull Locale locale,
                                                               @NotNull BiFunction<? super @NotNull ClassLoader, ? super Locale, ? extends @NotNull ResourceBundle> bundleResolver) {
    Path bundlePath = FileSystems.getDefault().getPath(FileUtil.toCanonicalPath(defaultPath, '.'));
    ClassLoader pluginClassLoader = languagePluginClassLoader(bundleClassLoader, locale);
    List<Path> paths = LocalizationUtil.INSTANCE.getLocalizedPaths(bundlePath, locale);
    Map<BundleOrder, ResourceBundle> bundleOrderMap = new HashMap<>();
    if (pluginClassLoader != null) {
      resolveBundleOrder(pluginClassLoader, true, bundlePath, paths, bundleOrderMap, bundleResolver, locale);
    }
    resolveBundleOrder(baseLoader, false, bundlePath, paths, bundleOrderMap, bundleResolver, locale);
    reorderParents(bundleOrderMap);
    Optional<Map.Entry<BundleOrder, ResourceBundle>> resourceBundleEntry = bundleOrderMap.entrySet().stream().min(Map.Entry.comparingByKey());
    if (!resourceBundleEntry.isPresent()) {
      throw new RuntimeException("No such resource bundle: " + bundlePath);
    }
    ResourceBundle bundle = resourceBundleEntry.get().getValue();
    bundles.put(defaultPath, bundle);
    return bundle;
  }

  private static @NotNull List<ResourceBundle> getBundlesFromLocalizationFolder(@NotNull Path pathToBundle, ClassLoader loader, @NotNull Locale locale) {
    List<Path> paths = LocalizationUtil.INSTANCE.getFolderLocalizedPaths(pathToBundle, locale);
    List<ResourceBundle> resourceBundles = new ArrayList<>();
    for (Path path : paths) {
      try {
        ResourceBundle resourceBundle = resolveBundle(loader, locale, FileUtil.toSystemIndependentName(path.toString()));
        resourceBundles.add(resourceBundle);
      }
      catch (MissingResourceException ignored) {
      }
    }

    if (resourceBundles.isEmpty()) {
      LOG.debug("No bundles found in: " + StringUtil.join(paths, ", "));
    }

    return resourceBundles;
  }

  private static @Nullable ClassLoader languagePluginClassLoader(@NotNull ClassLoader bundleClassLoader, @NotNull Locale locale) {
    if (DefaultBundleService.isDefaultBundle()) {
      return null;
    }
    LanguageBundleEP langBundle = findLanguageBundle();
    if (langBundle == null) {
      return null;
    }
    if (!Objects.equals(locale.getLanguage(), getLocale().getLanguage()) ||
        (locale.getCountry() != null && !Objects.equals(locale.getCountry(), getLocale().getCountry()))) {
      return null;
    }
    PluginDescriptor pluginDescriptor = langBundle.pluginDescriptor;
    return pluginDescriptor == null ? bundleClassLoader : pluginDescriptor.getClassLoader();
  }

  private static ResourceBundle getParent(@NotNull ResourceBundle bundle) throws Throwable {
    return (ResourceBundle)DynamicBundleInternal.GET_PARENT.invokeWithArguments(bundle);
  }

  private static void resolveBundleOrder(@NotNull ClassLoader loader,
                                         boolean isPluginClassLoader,
                                         @NotNull Path pathToBundle,
                                         @NotNull List<? extends Path> orderedPaths,
                                         @NotNull Map<? super BundleOrder, ? super ResourceBundle> bundleOrderMap,
                                         @NotNull BiFunction<? super @NotNull ClassLoader, ? super Locale, ? extends @NotNull ResourceBundle> bundleResolver,
                                         @NotNull Locale locale) {
    ResourceBundle bundle = bundleResolver.apply(loader, locale);
    try {
      while (bundle != null) {
        putBundleOrder(bundle, bundleOrderMap, orderedPaths, isPluginClassLoader);
        bundle = getParent(bundle);
      }
    }
    catch (Throwable throwable) {
      LOG.info(throwable);
    }
    for (ResourceBundle localizedBundle : getBundlesFromLocalizationFolder(pathToBundle, loader, locale)) {
      putBundleOrder(localizedBundle, bundleOrderMap, orderedPaths, isPluginClassLoader);
    }
  }

  private static void putBundleOrder(@NotNull ResourceBundle bundle,
                                     @NotNull Map<? super BundleOrder, ? super ResourceBundle> bundleOrderMap,
                                     @NotNull List<? extends Path> orderedPaths,
                                     boolean isPluginClassLoader) {
    String bundlePath = FileUtil.toCanonicalPath(bundle.getBaseBundleName(), '.');
    if (!bundle.getLocale().toString().isEmpty()) {
      bundlePath += "_" + bundle.getLocale().toString();
    }
    Path path = FileSystems.getDefault().getPath(bundlePath);
    BundleOrder bundleOrder = BundleOrder.getBundleOrder(orderedPaths, path, isPluginClassLoader);
    if (bundleOrder == null) {
      LOG.debug("Order cannot be defined for the bundle: " + path +
                "; Current locale: " + getLocale() +
                "; Paths for locale: " + orderedPaths);
      return;
    }
    bundleOrderMap.put(bundleOrder, bundle);
  }

  private static void reorderParents(@NotNull Map<BundleOrder, ResourceBundle> bundleOrderMap) {
    ResourceBundle resourceBundle = null;
    for (BundleOrder bundleOrder : BundleOrder.values()) {
      ResourceBundle parentBundle = bundleOrderMap.get(bundleOrder);
      if (parentBundle != null && parentBundle != resourceBundle) {
        if (resourceBundle != null) {
          try {
            DynamicBundleInternal.SET_PARENT.bindTo(resourceBundle).invoke(parentBundle);
          }
          catch (Throwable e) {
            LOG.warn(e);
          }
        }
        resourceBundle = parentBundle;
      }
    }
  }

  /**
   * "SET_PARENT" has been temporarily moved into the internal class to fix Kotlin compiler.
   * It's to be refactored with "ResourceBundleProvider" since 'core-api' module will use java 1.9+
   */
  private static class DynamicBundleInternal {
    @NotNull
    private static final MethodHandle SET_PARENT;
    @NotNull
    private static final MethodHandle GET_PARENT;

    static {
      try {
        Method method = ResourceBundle.class.getDeclaredMethod("setParent", ResourceBundle.class);
        method.setAccessible(true);
        SET_PARENT = MethodHandles.lookup().unreflect(method);

        Field parentField = ResourceBundle.class.getDeclaredField("parent");
        parentField.setAccessible(true);
        GET_PARENT = MethodHandles.lookup().unreflectGetter(parentField);
      }
      catch (ReflectiveOperationException e) {
        throw new RuntimeException(e);
      }
    }
  }

  // todo: one language per application
  public static @Nullable LanguageBundleEP findLanguageBundle() {
    try {
      if (!LoadingState.COMPONENTS_REGISTERED.isOccurred()) {
        return null;
      }

      Application app = ApplicationManager.getApplication();
      if (app == null || !app.getExtensionArea().hasExtensionPoint(LanguageBundleEP.EP_NAME)) {
        return null;
      }
      return LanguageBundleEP.EP_NAME.findExtension(LanguageBundleEP.class);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
      return null;
    }
  }

  @Override
  protected ResourceBundle getBundle(boolean isDefault) {
    ResourceBundle bundle = super.getBundle(isDefault);
    if (bundle != null && LocalizationUtil.INSTANCE.isL10nPluginInitialized() && isUpdateNeeded) {
      isUpdateNeeded = false;
      return null;
    }
    return bundle;
  }

  /**
   * @deprecated use {@link #getResourceBundle(ClassLoader, String)}
   */
  @Deprecated
  @ScheduledForRemoval
  public static final DynamicBundle INSTANCE = new DynamicBundle("") {
  };

  /**
   * @deprecated use {@link #getResourceBundle(ClassLoader, String)}
   */
  @SuppressWarnings("MethodMayBeStatic")
  @Deprecated
  @ScheduledForRemoval
  public final @NotNull ResourceBundle getResourceBundle(@NotNull @NonNls String pathToBundle, @NotNull ClassLoader loader) {
    return getResourceBundle(loader, pathToBundle);
  }

  @ApiStatus.Internal
  public static final class LanguageBundleEP implements PluginAware {
    public static final ExtensionPointName<LanguageBundleEP> EP_NAME = new ExtensionPointName<>("com.intellij.languageBundle");

    @Attribute("locale")
    public String locale = Locale.ENGLISH.getLanguage();
    public PluginDescriptor pluginDescriptor;

    @Override
    public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
      this.pluginDescriptor = pluginDescriptor;
    }
  }

  private static final Map<ClassLoader, Map<String, ResourceBundle>> ourCache = CollectionFactory.createConcurrentWeakMap();
  private static final Map<ClassLoader, Map<String, ResourceBundle>> ourDefaultCache = CollectionFactory.createConcurrentWeakMap();

  public static @NotNull ResourceBundle getResourceBundle(@NotNull ClassLoader loader, @NotNull @NonNls String pathToBundle) {
    return (DefaultBundleService.isDefaultBundle() ? ourDefaultCache : ourCache)
      .computeIfAbsent(loader, __ -> CollectionFactory.createConcurrentSoftValueMap())
      .computeIfAbsent(pathToBundle, __ -> resolveResourceBundle(loader, pathToBundle, getLocale()));
  }

  public static @NotNull ResourceBundle getResourceBundle(@NotNull ClassLoader loader, @NotNull @NonNls String pathToBundle, @NotNull Locale locale) {
    return (DefaultBundleService.isDefaultBundle() ? ourDefaultCache : ourCache)
      .computeIfAbsent(loader, __ -> CollectionFactory.createConcurrentSoftValueMap())
      .computeIfAbsent(pathToBundle, __ -> resolveResourceBundle(loader, pathToBundle, locale));
  }

  public static @Nullable ResourceBundle getPluginBundle(@NotNull PluginDescriptor pluginDescriptor) {
    ClassLoader classLoader = pluginDescriptor.getPluginClassLoader();
    String baseName = pluginDescriptor.getResourceBundleBaseName();
    return classLoader != null && baseName != null ? getResourceBundle(classLoader, baseName) : null;
  }

  private static @NotNull ResourceBundle resolveResourceBundle(@NotNull ClassLoader loader, @NonNls @NotNull String pathToBundle, @NotNull Locale locale) {
    return resolveResourceBundleWithFallback(loader, pathToBundle, () -> resolveResourceBundle(DynamicBundle.class.getClassLoader(),
                                                                                               loader, pathToBundle, locale,
                                                                                               bundleResolver(pathToBundle)));
  }

  private static @NotNull BiFunction<@NotNull ClassLoader, @NotNull Locale, @NotNull ResourceBundle> bundleResolver(@NonNls @NotNull String pathToBundle) {
    return (loader, locale) -> resolveBundle(loader, locale, pathToBundle);
  }

  /**
   * @deprecated used only by GUI form builder
   */
  @Deprecated
  public static ResourceBundle getBundle(@NotNull String baseName) {
    Class<?> callerClass = ReflectionUtil.findCallerClass(2);
    return getBundle(baseName, callerClass == null ? DynamicBundle.class : callerClass);
  }

  /**
   * @deprecated used only by GUI form builder
   */
  @Deprecated
  public static ResourceBundle getBundle(@NotNull String baseName, @NotNull Class<?> formClass) {
    ResourceBundle rb = getResourceBundle(formClass.getClassLoader(), baseName);
    if (!BundleBase.SHOW_LOCALIZED_MESSAGES) {
      return rb;
    }

    return new ResourceBundle() {
      @Override
      protected Object handleGetObject(@NotNull String key) {
        Object get = rb.getObject(key);
        assert get instanceof String : "Language bundles should contain only strings";
        return BundleBase.appendLocalizationSuffix((String)get, BundleBase.L10N_MARKER);
      }

      @Override
      public @NotNull Enumeration<String> getKeys() {
        return rb.getKeys();
      }
    };
  }

  public static void loadLocale(@Nullable LanguageBundleEP langBundle) {
    if (langBundle != null) {
      ourLangTag = langBundle.locale;
      ourCache.clear();
      return;
    }
    // TODO: this is a temporary solutions, should be done better in IJPL-148813
    String i18Locale = System.getProperty("intellij.searchableOptions.i18n.locale", Locale.ENGLISH.toLanguageTag());
    if (!Locale.ENGLISH.toLanguageTag().equals(i18Locale)) {
      ourLangTag = i18Locale;
      ourCache.clear();
    }
  }

  public static @NotNull Locale getLocale() {
    return Locale.forLanguageTag(ourLangTag);
  }

  @ApiStatus.Internal
  @NotNull @Unmodifiable
  public static Map<String, ResourceBundle> getResourceBundles() {
    return Collections.unmodifiableMap(bundles);
  }

  private enum BundleOrder {
    FOLDER_REGION_LEVEL_PLUGIN, //localization/zh/CN/
    FOLDER_REGION_LEVEL_PLATFORM,
    SUFFIX_REGION_LEVEL_PLUGIN, //name_zh_CN.properties
    SUFFIX_REGION_LEVEL_PLATFORM,
    FOLDER_LANGUAGE_LEVEL_PLUGIN, //localization/zh/
    FOLDER_LANGUAGE_LEVEL_PLATFORM,
    SUFFIX_LANGUAGE_LEVEL_PLUGIN, //name_zh.properties
    SUFFIX_LANGUAGE_LEVEL_PLATFORM,
    DEFAULT_PLUGIN, //name.properties
    DEFAULT_PLATFORM;

    @Nullable
    static BundleOrder getBundleOrder(@NotNull List<? extends Path> orderedPaths, @NotNull Path bundlePath, boolean isPluginClassLoader) {
      int order = orderedPaths.indexOf(bundlePath);
      order = isPluginClassLoader ? order * 2 : order * 2 + 1;
      return 0 <= order && order < values().length ? values()[order] : null;
    }
  }
}
