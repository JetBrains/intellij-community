// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.extensions.impl.InterfaceExtensionPoint;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Extensions {
  public static final ExtensionPointName<AreaListener> AREA_LISTENER_EXTENSION_POINT = new ExtensionPointName<>("com.intellij.arealistener");
  private static final Map<AreaInstance, ExtensionsAreaImpl> ourAreaInstance2area = ContainerUtil.newConcurrentMap();
  private static final Map<String, AreaClassConfiguration> ourAreaClass2Configuration = ContainerUtil.newConcurrentMap();

  @NotNull
  private static ExtensionsAreaImpl ourRootArea = createRootArea();

  private Extensions() {
  }

  @NotNull
  private static ExtensionsAreaImpl createRootArea() {
    ExtensionsAreaImpl rootArea = new ExtensionsAreaImpl(null, null, null);
    rootArea.registerExtensionPoint(new InterfaceExtensionPoint<>(AREA_LISTENER_EXTENSION_POINT.getName(), AreaListener.class, rootArea.getPicoContainer()));
    return rootArea;
  }

  /**
   * @return instance containing application-level extensions
   */
  @NotNull
  public static ExtensionsArea getRootArea() {
    return ourRootArea;
  }

  /**
   * If {@code areaInstance} is a project returns instance containing project-level extensions for that project
   * if {@code areaInstance} is a module returns instance containing module-level extensions for that module,
   * if {@code areaInstance} is {@code null} returns instance containing application-level extensions.
   */
  @NotNull
  public static ExtensionsArea getArea(@Nullable("null means root") AreaInstance areaInstance) {
    if (areaInstance == null) {
      return ourRootArea;
    }
    ExtensionsAreaImpl area = ourAreaInstance2area.get(areaInstance);
    if (area == null) {
      throw new IllegalArgumentException("No area instantiated for: " + areaInstance);
    }
    return area;
  }

  @TestOnly
  public static void cleanRootArea(@NotNull Disposable parentDisposable) {
    final ExtensionsAreaImpl oldRootArea = (ExtensionsAreaImpl)getRootArea();
    final ExtensionsAreaImpl newArea = createRootArea();
    ourRootArea = newArea;
    oldRootArea.notifyAreaReplaced(newArea);
    Disposer.register(parentDisposable, () -> {
      ourRootArea = oldRootArea;
      newArea.notifyAreaReplaced(oldRootArea);
    });
  }

  /**
   * @deprecated Use {@link ExtensionPointName#getExtensions()}
   */
  @NotNull
  @Deprecated
  public static Object[] getExtensions(@NonNls @NotNull String extensionPointName) {
    return getRootArea().getExtensionPoint(extensionPointName).getExtensions();
  }

  /**
   * @deprecated Use {@link ExtensionPointName#getExtensionList()}
   */
  @Deprecated
  @NotNull
  public static <T> T[] getExtensions(@NotNull ExtensionPointName<T> extensionPointName) {
    return extensionPointName.getExtensions();
  }

  /**
   * @deprecated Use {@link ProjectExtensionPointName#getExtensions(AreaInstance)}
   */
  @Deprecated
  @NotNull
  public static <T> T[] getExtensions(@NotNull ExtensionPointName<T> extensionPointName, @Nullable AreaInstance areaInstance) {
    return extensionPointName.getExtensions(areaInstance);
  }

  /**
   * @deprecated Use {@link ExtensionPointName#getExtensions()}
   */
  @Deprecated
  @NotNull
  public static <T> T[] getExtensions(@NotNull String extensionPointName, @Nullable("null means root") AreaInstance areaInstance) {
    return getArea(areaInstance).<T>getExtensionPoint(extensionPointName).getExtensions();
  }

  /**
   * @deprecated Use {@link ExtensionPointName#findExtensionOrFail(Class)}
   */
  @Deprecated
  @NotNull
  public static <T, U extends T> U findExtension(@NotNull ExtensionPointName<T> extensionPointName, @NotNull Class<U> extClass) {
    return extensionPointName.findExtensionOrFail(extClass);
  }

  /**
   * @deprecated Use {@link ExtensionPointName#findExtensionOrFail(Class)}
   */
  @Deprecated
  @NotNull
  public static <T, U extends T> U findExtension(@NotNull ExtensionPointName<T> extensionPointName, AreaInstance areaInstance, @NotNull Class<U> extClass) {
    return extensionPointName.findExtensionOrFail(extClass, areaInstance);
  }

  public static void instantiateArea(@NonNls @NotNull String areaClass, @NotNull AreaInstance areaInstance, @Nullable("null means root") AreaInstance parentAreaInstance) {
    AreaClassConfiguration configuration = ourAreaClass2Configuration.get(areaClass);
    if (configuration == null) {
      throw new IllegalArgumentException("Area class is not registered: " + areaClass);
    }
    ExtensionsArea parentArea = getArea(parentAreaInstance);
    if (!Objects.equals(parentArea.getAreaClass(), configuration.getParentClassName())) {
      throw new IllegalArgumentException("Wrong parent area. Expected class: " + configuration.getParentClassName() + " actual class: " + parentArea.getAreaClass());
    }
    ExtensionsAreaImpl area = new ExtensionsAreaImpl(areaClass, areaInstance, parentArea.getPicoContainer());
    if (ourAreaInstance2area.put(areaInstance, area) != null) {
      throw new IllegalArgumentException("Area already instantiated for: " + areaInstance);
    }
    for (AreaListener listener : getAreaListeners()) {
      listener.areaCreated(areaClass, areaInstance);
    }
  }

  @NotNull
  private static List<AreaListener> getAreaListeners() {
    return getRootArea().getExtensionPoint(AREA_LISTENER_EXTENSION_POINT).getExtensionList();
  }

  public static void registerAreaClass(@NonNls @NotNull String areaClass, @Nullable @NonNls String parentAreaClass) {
    if (ourAreaClass2Configuration.containsKey(areaClass)) {
      // allow duplicate area class registrations if they are the same - fixing duplicate registration in tests is much more trouble
      AreaClassConfiguration configuration = ourAreaClass2Configuration.get(areaClass);
      if (!Objects.equals(configuration.getParentClassName(), parentAreaClass)) {
        throw new RuntimeException("Area class already registered: " + areaClass + ", "+ configuration);
      }
      else {
        return;
      }
    }
    AreaClassConfiguration configuration = new AreaClassConfiguration(areaClass, parentAreaClass);
    ourAreaClass2Configuration.put(areaClass, configuration);
  }

  public static void disposeArea(@NotNull AreaInstance areaInstance) {
    assert ourAreaInstance2area.containsKey(areaInstance);

    String areaClass = ourAreaInstance2area.get(areaInstance).getAreaClass();
    if (areaClass == null) {
      throw new IllegalArgumentException("Area class is null (area never instantiated?). Instance: " + areaInstance);
    }
    try {
      for (AreaListener listener : getAreaListeners()) {
        listener.areaDisposing(areaClass, areaInstance);
      }
    }
    finally {
      ourAreaInstance2area.remove(areaInstance);
    }
  }

  private static class AreaClassConfiguration {
    private final String myClassName;
    private final String myParentClassName;

    private AreaClassConfiguration(@NotNull String className, String parentClassName) {
      myClassName = className;
      myParentClassName = parentClassName;
    }

    @NotNull
    public String getClassName() {
      return myClassName;
    }

    public String getParentClassName() {
      return myParentClassName;
    }

    @Override
    public String toString() {
      return "AreaClassConfiguration{myClassName='" + myClassName + '\'' + ", myParentClassName='" + myParentClassName + "'}";
    }
  }

  public enum OS {
    mac, linux, windows, unix, freebsd
  }
}
