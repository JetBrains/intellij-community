/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.extensions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Map;

public class Extensions {
  public static final ExtensionPointName<AreaListener> AREA_LISTENER_EXTENSION_POINT = new ExtensionPointName<AreaListener>("com.intellij.arealistener");
  private static LogProvider ourLogger = new SimpleLogProvider();
  private static final Map<AreaInstance, ExtensionsAreaImpl> ourAreaInstance2area = ContainerUtil.newConcurrentMap();
  private static final Map<String, AreaClassConfiguration> ourAreaClass2Configuration = ContainerUtil.newConcurrentMap();

  @NotNull private static ExtensionsAreaImpl ourRootArea = createRootArea();

  private Extensions() {
  }

  @NotNull
  private static ExtensionsAreaImpl createRootArea() {
    ExtensionsAreaImpl rootArea = new ExtensionsAreaImpl(null, null, null, ourLogger);
    rootArea.registerExtensionPoint(AREA_LISTENER_EXTENSION_POINT.getName(), AreaListener.class.getName());
    return rootArea;
  }

  @NotNull
  public static ExtensionsArea getRootArea() {
    return ourRootArea;
  }

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
    oldRootArea.notifyAreaReplaced();
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        ourRootArea = oldRootArea;
        newArea.notifyAreaReplaced();
      }
    });
  }

  @NotNull
  public static Object[] getExtensions(@NonNls String extensionPointName) {
    return getExtensions(extensionPointName, null);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public static <T> T[] getExtensions(@NotNull ExtensionPointName<T> extensionPointName) {
    return (T[])getExtensions(extensionPointName.getName(), null);
  }

  @NotNull
  public static <T> T[] getExtensions(@NotNull ExtensionPointName<T> extensionPointName, AreaInstance areaInstance) {
    // keep it until 1.7 JDK
    return Extensions.<T>getExtensions(extensionPointName.getName(), areaInstance);
  }

  @NotNull
  public static <T> T[] getExtensions(String extensionPointName, @Nullable("null means root") AreaInstance areaInstance) {
    ExtensionsArea area = getArea(areaInstance);
    ExtensionPoint<T> extensionPoint = area.getExtensionPoint(extensionPointName);
    return extensionPoint.getExtensions();
  }

  @NotNull
  public static <T, U extends T> U findExtension(@NotNull ExtensionPointName<T> extensionPointName, @NotNull Class<U> extClass) {
    for (T t : getExtensions(extensionPointName)) {
      if (extClass.isInstance(t)) {
        //noinspection unchecked
        return (U) t;
      }
    }
    throw new IllegalArgumentException("could not find extension implementation " + extClass);
  }

  @NotNull
  public static <T, U extends T> U findExtension(@NotNull ExtensionPointName<T> extensionPointName, AreaInstance areaInstance, @NotNull Class<U> extClass) {
    for (T t : getExtensions(extensionPointName, areaInstance)) {
      if (extClass.isInstance(t)) {
        //noinspection unchecked
        return (U) t;
      }
    }
    throw new IllegalArgumentException("could not find extension implementation " + extClass);
  }

  public static void instantiateArea(@NonNls @NotNull String areaClass, @NotNull AreaInstance areaInstance, @Nullable("null means root") AreaInstance parentAreaInstance) {
    AreaClassConfiguration configuration = ourAreaClass2Configuration.get(areaClass);
    if (configuration == null) {
      throw new IllegalArgumentException("Area class is not registered: " + areaClass);
    }
    ExtensionsArea parentArea = getArea(parentAreaInstance);
    if (!equals(parentArea.getAreaClass(), configuration.getParentClassName())) {
      throw new IllegalArgumentException("Wrong parent area. Expected class: " + configuration.getParentClassName() + " actual class: " + parentArea.getAreaClass());
    }
    ExtensionsAreaImpl area = new ExtensionsAreaImpl(areaClass, areaInstance, parentArea.getPicoContainer(), ourLogger);
    if (ourAreaInstance2area.put(areaInstance, area) != null) {
      throw new IllegalArgumentException("Area already instantiated for: " + areaInstance);
    }
    for (AreaListener listener : getAreaListeners()) {
      listener.areaCreated(areaClass, areaInstance);
    }
  }

  @NotNull
  private static AreaListener[] getAreaListeners() {
    return getRootArea().getExtensionPoint(AREA_LISTENER_EXTENSION_POINT).getExtensions();
  }

  public static void registerAreaClass(@NonNls @NotNull String areaClass, @Nullable @NonNls String parentAreaClass) {
    if (ourAreaClass2Configuration.containsKey(areaClass)) {
      // allow duplicate area class registrations if they are the same - fixing duplicate registration in tests is much more trouble
      AreaClassConfiguration configuration = ourAreaClass2Configuration.get(areaClass);
      if (!equals(configuration.getParentClassName(), parentAreaClass)) {
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

  private static boolean equals(@Nullable Object object1, @Nullable Object object2) {
    return object1 == object2 || object1 != null && object2 != null && object1.equals(object2);
  }

  public static void setLogProvider(@NotNull LogProvider logProvider) {
    ourLogger = logProvider;
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

  @SuppressWarnings("CallToPrintStackTrace")
  public static class SimpleLogProvider implements LogProvider {
    @Override
    public void error(String message) {
      new Throwable(message).printStackTrace();
    }

    @Override
    public void error(String message, @NotNull Throwable t) {
      System.err.println(message);
      t.printStackTrace();
    }

    @Override
    public void error(@NotNull Throwable t) {
      t.printStackTrace();
    }

    @Override
    public void warn(String message) {
      System.err.println(message);
    }

    @Override
    public void warn(String message, @NotNull Throwable t) {
      System.err.println(message);
      t.printStackTrace();
    }

    @Override
    public void warn(@NotNull Throwable t) {
      t.printStackTrace();
    }
  }

  public static boolean isComponentSuitableForOs(@Nullable String os) {
    if (StringUtil.isEmpty(os)) {
      return true;
    }

    if (os.equals("mac")) {
      return SystemInfoRt.isMac;
    }
    else if (os.equals("linux")) {
      return SystemInfoRt.isLinux;
    }
    else if (os.equals("windows")) {
      return SystemInfoRt.isWindows;
    }
    else if (os.equals("unix")) {
      return SystemInfoRt.isUnix;
    }
    else if (os.equals("freebsd")) {
      return SystemInfoRt.isFreeBSD;
    }
    else {
      ourLogger.warn("Unknown OS " + os);
      return true;
    }
  }
}
