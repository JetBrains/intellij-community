// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.l10n.LocalizationOrder;
import com.intellij.l10n.LocalizationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.DefaultBundleService;
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
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

public class DynamicBundle extends AbstractBundle {
  private static final Logger LOG = Logger.getInstance(DynamicBundle.class);

  private static final ConcurrentMap<String, ResourceBundle> bundles = CollectionFactory.createConcurrentWeakValueMap();
  /**
   * Creates a new instance of the message bundle. It's usually stored in a private static final field, and static methods delegating
   * to its {@link #getMessage} and {@link #getLazyMessage} methods are added.
   *
   * @param bundleClass  any class from the module containing the bundle, it's used to locate the file with the messages
   * @param pathToBundle qualified name of the file with the messages (without the extension, with slashes replaced by dots)
   */
  public DynamicBundle(@NotNull Class<?> bundleClass, @NotNull String pathToBundle) {
    super(bundleClass, pathToBundle);
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
  }

  // see BundleUtil
  @Override
  protected @NotNull ResourceBundle findBundle(@NotNull String pathToBundle,
                                               @NotNull ClassLoader baseLoader,
                                               @NotNull ResourceBundle.Control control) {
    return (DefaultBundleService.isDefaultBundle() ? ourDefaultCache : ourCache)
      .computeIfAbsent(baseLoader, __ -> CollectionFactory.createConcurrentSoftValueMap())
      .computeIfAbsent(pathToBundle, __ ->
        resolveResourceBundle(
          getBundleClassLoader(),
          baseLoader,
          pathToBundle,
          getResolveLocale(),
          (loader, locale) -> super.findBundle(pathToBundle, loader, control, locale)
        ));
  }

  private static ResourceBundle getBundleFromCache(@NotNull ClassLoader loader, @NotNull String pathToBundle) {
    Map<String, ResourceBundle> loaderCache = ourCache.get(loader);
    if (loaderCache == null) return null;
    return loaderCache.get(pathToBundle);
  }
  
  private static @NotNull ResourceBundle resolveResourceBundle(@NotNull ClassLoader bundleClassLoader,
                                                               @NotNull ClassLoader baseLoader,
                                                               @NotNull String defaultPath,
                                                               @NotNull Locale locale,
                                                               @NotNull BiFunction<? super @NotNull ClassLoader, ? super Locale, ? extends @NotNull ResourceBundle> bundleResolver) {
    String bundlePath = FileUtilRt.toCanonicalPath(defaultPath, '.', true);
    ClassLoader pluginClassLoader = DefaultBundleService.isDefaultBundle() ? null: LocalizationUtil.INSTANCE.getPluginClassLoader(bundleClassLoader, locale);
    List<String> paths = LocalizationUtil.INSTANCE.getLocalizedPathsWithDefault(bundlePath, locale);
    Map<LocalizationOrder, ResourceBundle> bundleOrderMap = new HashMap<>();
    if (pluginClassLoader != null) {
      try {
        ResourceBundle pluginBundle = bundleResolver.apply(pluginClassLoader, Locale.ROOT);
        bundleOrderMap.put(LocalizationOrder.DEFAULT_PLUGIN, pluginBundle);
      }
      catch (MissingResourceException e) {
        LOG.debug("Bundle " + defaultPath + " was not found in localization plugin for locale: " + locale.toLanguageTag());
      }
    }
    resolveBundleOrder(baseLoader, bundlePath, paths, bundleOrderMap, bundleResolver, locale);
    reorderParents(bundleOrderMap);
    Optional<Map.Entry<LocalizationOrder, ResourceBundle>> resourceBundleEntry = bundleOrderMap.entrySet().stream().min(Map.Entry.comparingByKey());
    if (!resourceBundleEntry.isPresent()) {
      throw new RuntimeException("No such resource bundle: " + bundlePath);
    }
    ResourceBundle bundle = resourceBundleEntry.get().getValue();
    bundles.put(defaultPath, bundle);
    return bundle;
  }

  private static @NotNull List<ResourceBundle> getBundlesFromLocalizationFolder(@NotNull String pathToBundle, ClassLoader loader, @NotNull Locale locale) {
    List<String> paths = LocalizationUtil.INSTANCE.getFolderLocalizedPaths(pathToBundle, locale);
    List<ResourceBundle> resourceBundles = new ArrayList<>();
    for (String path : paths) {
      try {
        resourceBundles.add(AbstractBundleKt._doResolveBundle(loader, locale, path));
      }
      catch (MissingResourceException ignored) { }
    }

    if (resourceBundles.isEmpty()) {
      LOG.debug("No bundles found in: " + StringUtil.join(paths, ", "));
    }

    return resourceBundles;
  }

  private static ResourceBundle getParent(@NotNull ResourceBundle bundle) throws Throwable {
    return (ResourceBundle)DynamicBundleInternal.GET_PARENT.invokeWithArguments(bundle);
  }

  private static void resolveBundleOrder(@NotNull ClassLoader loader,
                                         @NotNull String pathToBundle,
                                         @NotNull List<String> orderedPaths,
                                         @NotNull Map<? super LocalizationOrder, ? super ResourceBundle> bundleOrderMap,
                                         @NotNull BiFunction<? super @NotNull ClassLoader, ? super Locale, ? extends @NotNull ResourceBundle> bundleResolver,
                                         @NotNull Locale locale) {
    ResourceBundle bundle = bundleResolver.apply(loader, locale);
    try {
      while (bundle != null) {
        putBundleOrder(bundle, bundleOrderMap, orderedPaths);
        bundle = getParent(bundle);
      }
    }
    catch (Throwable throwable) {
      LOG.info(throwable);
    }
    for (ResourceBundle localizedBundle : getBundlesFromLocalizationFolder(pathToBundle, loader, locale)) {
      putBundleOrder(localizedBundle, bundleOrderMap, orderedPaths);
    }
  }

  private static void putBundleOrder(@NotNull ResourceBundle bundle,
                                     @NotNull Map<? super LocalizationOrder, ? super ResourceBundle> bundleOrderMap,
                                     @NotNull List<String> orderedPaths) {
    String bundlePath = FileUtil.toCanonicalPath(bundle.getBaseBundleName(), '.');

    if (!bundle.getLocale().toString().isEmpty()) {
      bundlePath += "_" + bundle.getLocale().toString();
    }
    LocalizationOrder localizationOrder = LocalizationOrder.Companion.getLocalizationOrder(orderedPaths, bundlePath);
    if (localizationOrder == null) {
      LOG.debug("Order cannot be defined for the bundle: " + bundlePath +
                "; Current locale: " + getLocale() +
                "; Paths for locale: " + orderedPaths);
      return;
    }
    bundleOrderMap.put(localizationOrder, bundle);
  }

  private static void reorderParents(@NotNull Map<LocalizationOrder, ResourceBundle> bundleOrderMap) {
    ResourceBundle resourceBundle = null;
    for (LocalizationOrder localizationOrder : LocalizationOrder.getEntries()) {
      ResourceBundle parentBundle = bundleOrderMap.get(localizationOrder);
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

  @Override
  @ApiStatus.Internal
  protected ResourceBundle getBundle(boolean isDefault, @NotNull ClassLoader classLoader) {
    ResourceBundle bundle = super.getBundle(isDefault, classLoader);
    if (bundle != null &&
        !isDefault &&
        (getBundleFromCache(classLoader, bundle.getBaseBundleName()) == null ||
         getBundleFromCache(classLoader, bundle.getBaseBundleName()) != bundle)) {
      LOG.info("Cleanup bundle cache for " + bundle.getBaseBundleName());
      return null;
    }
    return bundle;
  }
  
  @ApiStatus.Internal
  public static void clearCache() {
    ourCache.clear();
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

    @Attribute("displayName")
    public String displayName;

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
      .computeIfAbsent(pathToBundle, __ -> resolveResourceBundle(loader, pathToBundle, getResolveLocale()));
  }

  public static @NotNull ResourceBundle getResourceBundle(@NotNull ClassLoader loader, @NotNull @NonNls String pathToBundle, @NotNull Locale locale) {
    return (DefaultBundleService.isDefaultBundle() ? ourDefaultCache : ourCache)
      .computeIfAbsent(loader, __ -> CollectionFactory.createConcurrentSoftValueMap())
      .computeIfAbsent(pathToBundle, __ -> resolveResourceBundle(loader, pathToBundle, locale));
  }

  @ApiStatus.Internal
  public static @NotNull ResourceBundle getResourceBundleLocalized(@NotNull ClassLoader loader, @NotNull @NonNls String pathToBundle, @NotNull Locale locale) {
    ResourceBundle bundle = (DefaultBundleService.isDefaultBundle() ? ourDefaultCache : ourCache)
      .computeIfAbsent(loader, __ -> CollectionFactory.createConcurrentSoftValueMap())
      .get(pathToBundle);
    if (bundle != null && bundle.getLocale().equals(locale)) {
      return bundle;
    }
    return resolveResourceBundle(loader, pathToBundle, locale);
  }

  public static @Nullable ResourceBundle getPluginBundle(@NotNull PluginDescriptor pluginDescriptor) {
    ClassLoader classLoader = pluginDescriptor.getPluginClassLoader();
    String baseName = pluginDescriptor.getResourceBundleBaseName();
    return classLoader != null && baseName != null ? getResourceBundle(classLoader, baseName) : null;
  }

  private static @NotNull ResourceBundle resolveResourceBundle(
    @NotNull ClassLoader loader,
    @NonNls @NotNull String pathToBundle,
    @NotNull Locale locale
  ) {
    return Companion.resolveResourceBundleWithFallback(loader, pathToBundle, () -> {
      return resolveResourceBundle(
        DynamicBundle.class.getClassLoader(),
        loader,
        pathToBundle,
        locale,
        bundleResolver(pathToBundle)
      );
    });
  }

  private static @NotNull BiFunction<@NotNull ClassLoader, @NotNull Locale, @NotNull ResourceBundle> bundleResolver(@NonNls @NotNull String pathToBundle) {
    return (loader, locale) -> AbstractBundleKt._doResolveBundle(loader, locale, pathToBundle);
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
        return BundleBase.INSTANCE.appendLocalizationSuffix((String)get, BundleBase.L10N_MARKER);
      }

      @Override
      public @NotNull Enumeration<String> getKeys() {
        return rb.getKeys();
      }
    };
  }

  public static @NotNull Locale getLocale() {
    return LocalizationUtil.INSTANCE.getLocale();
  }

  /**
   * @return Locale used to resolve messages
   */
  private static @NotNull Locale getResolveLocale() {
    Locale resolveLocale = getLocale();
    // we must use Locale.ROOT to get English messages from default bundles
    return resolveLocale.equals(Locale.ENGLISH) ? Locale.ROOT : resolveLocale;
  }

  @ApiStatus.Internal
  @NotNull
  @Unmodifiable
  public static Map<String, ResourceBundle> getResourceBundles() {
    return Collections.unmodifiableMap(bundles);
  }

}
