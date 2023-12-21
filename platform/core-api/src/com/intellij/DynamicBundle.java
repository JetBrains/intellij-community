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
import com.intellij.util.DefaultBundleService;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

public class DynamicBundle extends AbstractBundle {
  private static final Logger LOG = Logger.getInstance(DynamicBundle.class);

  private static @NotNull String ourLangTag = Locale.ENGLISH.toLanguageTag();

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
   * Use this constructor in bundle classes which inherit from this class.
   * Note that it's better to prefer delegation to inheritance, and use {@link #DynamicBundle(Class, String)} instead.
   */
  protected DynamicBundle(@NotNull String pathToBundle) {
    super(pathToBundle);
  }

  // see BundleUtil
  @Override
  protected @NotNull ResourceBundle findBundle(
    @NotNull String pathToBundle,
    @NotNull ClassLoader baseLoader,
    @NotNull ResourceBundle.Control control
  ) {
    return resolveResourceBundle(
      getBundleClassLoader(),
      baseLoader,
      loader -> {
        return super.findBundle(pathToBundle, loader, control, getLocale());
      }
    );
  }

  private static @NotNull ResourceBundle resolveResourceBundle(
    @NotNull ClassLoader bundleClassLoader,
    @NotNull ClassLoader baseLoader,
    @NotNull Function<? super @NotNull ClassLoader, ? extends @NotNull ResourceBundle> bundleResolver
  ) {
    ResourceBundle base = bundleResolver.apply(baseLoader);
    ClassLoader pluginClassLoader = languagePluginClassLoader(bundleClassLoader);
    if (pluginClassLoader == null) {
      return base;
    }
    ResourceBundle pluginBundle = bundleResolver.apply(pluginClassLoader);
    if (!setBundleParent(pluginBundle, base)) {
      return base;
    }
    return pluginBundle;
  }

  private static @Nullable ClassLoader languagePluginClassLoader(@NotNull ClassLoader bundleClassLoader) {
    if (DefaultBundleService.isDefaultBundle()) {
      return null;
    }
    LanguageBundleEP langBundle = findLanguageBundle();
    if (langBundle == null) {
      return null;
    }
    PluginDescriptor pluginDescriptor = langBundle.pluginDescriptor;
    return pluginDescriptor == null ? bundleClassLoader
                                    : pluginDescriptor.getClassLoader();
  }

  private static boolean setBundleParent(@NotNull ResourceBundle pluginBundle, ResourceBundle base) {
    if (pluginBundle == base) {
      return true;
    }
    try {
      Map<Integer, ResourceBundle> bundles = new HashMap<>();
      addBundleWithParents(pluginBundle, true, bundles);
      addBundleWithParents(base, false, bundles);
      reorderParents(bundles);
      return true;
    }
    catch (Throwable e) {
      LOG.warn(e);
      return false;
    }
  }

  private static void addBundleWithParents(ResourceBundle bundle, boolean isPluginBundle, Map<Integer, ResourceBundle> bundles)
    throws Throwable {
    bundles.put(getPriority(bundle, isPluginBundle), bundle);
    ResourceBundle parent = getParent(bundle);
    while (parent != null) {
      bundles.put(getPriority(parent, isPluginBundle), parent);
      parent = getParent(parent);
    }
  }

  private static ResourceBundle getParent(ResourceBundle bundle) throws Throwable {
    if (DynamicBundleInternal.GET_PARENT != null) {
      return (ResourceBundle)DynamicBundleInternal.GET_PARENT.invokeWithArguments(bundle);
    }
    return null;
  }

  /* 5 - plugin region localization
   * 4 - platform region localization
   * 3 - plugin language localization
   * 2 - platform language localization
   * 1 - plugin default localization
   * 0 - platform default localization
   * */
  private static int getPriority(ResourceBundle bundle, Boolean isPluginBundle) {
    int result = isPluginBundle ? 1 : 0;
    if (isRegionBundle(bundle)) {
      result += 4;
    }
    else if (!isDefaultBundle(bundle)) {
      result += 2;
    }
    return result;
  }

  private static boolean isRegionBundle(ResourceBundle bundle) {
    return !bundle.getLocale().getCountry().isEmpty();
  }

  private static boolean isDefaultBundle(ResourceBundle bundle) {
    return bundle.getLocale().getLanguage().isEmpty();
  }

  private static void reorderParents(Map<Integer, ResourceBundle> bundlesWithPriority) throws Throwable {
    int currentValue = 0;
    ResourceBundle parentBundle = null;
    while (currentValue <= 5) {
      ResourceBundle resourceBundle = bundlesWithPriority.get(currentValue);
      if (resourceBundle != null && resourceBundle != parentBundle) {
        if (parentBundle != null) {
          if (DynamicBundleInternal.SET_PARENT != null) {
            DynamicBundleInternal.SET_PARENT.bindTo(resourceBundle).invoke(parentBundle);
          }
        }
        parentBundle = resourceBundle;
      }
      currentValue++;
    }
  }

  /**
   * "SET_PARENT" has been temporarily moved into the internal class to fix Kotlin compiler.
   * It's to be refactored with "ResourceBundleProvider" since 'core-api' module will use java 1.9+
   */
  private static class DynamicBundleInternal {

    private static final MethodHandle SET_PARENT;
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
      catch (NoSuchMethodException | IllegalAccessException | NoSuchFieldException e) {
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
      .computeIfAbsent(pathToBundle, __ -> resolveResourceBundle(loader, pathToBundle));
  }

  public static @Nullable ResourceBundle getPluginBundle(@NotNull PluginDescriptor pluginDescriptor) {
    ClassLoader classLoader = pluginDescriptor.getPluginClassLoader();
    String baseName = pluginDescriptor.getResourceBundleBaseName();
    return classLoader != null && baseName != null ? getResourceBundle(classLoader, baseName) : null;
  }

  private static @NotNull ResourceBundle resolveResourceBundle(@NotNull ClassLoader loader, @NonNls @NotNull String pathToBundle) {
    return resolveResourceBundleWithFallback(
      () -> resolveResourceBundle(
        DynamicBundle.class.getClassLoader(),
        loader,
        bundleResolver(pathToBundle)
      ),
      loader, pathToBundle
    );
  }

  private static @NotNull Function<@NotNull ClassLoader, @NotNull ResourceBundle> bundleResolver(@NonNls @NotNull String pathToBundle) {
    return l -> AbstractBundle.resolveBundle(l, getLocale(), pathToBundle);
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
    }
  }

  public static @NotNull Locale getLocale() {
    return Locale.forLanguageTag(ourLangTag);
  }
}
