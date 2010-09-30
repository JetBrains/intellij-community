/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Extensions {
  private static LogProvider ourLogger = new SimpleLogProvider();

  public static final ExtensionPointName<AreaListener> AREA_LISTENER_EXTENSION_POINT = new ExtensionPointName<AreaListener>("com.intellij.arealistener");

  private static final Map<AreaInstance,ExtensionsAreaImpl> ourAreaInstance2area = new HashMap<AreaInstance, ExtensionsAreaImpl>();
  private static final MultiMap<String, AreaInstance> ourAreaClass2instances = new MultiMap<String, AreaInstance>();
  private static final Map<AreaInstance,String> ourAreaInstance2class = new HashMap<AreaInstance, String>();
  private static final Map<String,AreaClassConfiguration> ourAreaClass2Configuration = new HashMap<String, AreaClassConfiguration>();

  static {
    createRootArea();
  }

  private static ExtensionsAreaImpl createRootArea() {
    ExtensionsAreaImpl rootArea = new ExtensionsAreaImpl(null, null, null, ourLogger);
    rootArea.registerExtensionPoint(AREA_LISTENER_EXTENSION_POINT.getName(), AreaListener.class.getName());
    ourAreaInstance2area.put(null, rootArea);
    return rootArea;
  }

  private Extensions() {
  }

  public static ExtensionsArea getRootArea() {
    return getArea(null);
  }

  @NotNull
  public static ExtensionsArea getArea(@Nullable AreaInstance areaInstance) {
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
    oldRootArea.notifyAreaReplaced();
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        ourAreaInstance2area.put(null, oldRootArea);
        newArea.notifyAreaReplaced();
      }
    });
  }

  public static Object[] getExtensions(@NonNls String extensionPointName) {
    return getExtensions(extensionPointName, null);
  }

  @SuppressWarnings({"unchecked"})
  public static <T> T[] getExtensions(ExtensionPointName<T> extensionPointName) {
    return (T[])getExtensions(extensionPointName.getName(), null);
  }


  @SuppressWarnings({"unchecked"})
  public static <T> T[] getExtensions(ExtensionPointName<T> extensionPointName, AreaInstance areaInstance) {
    return Extensions.<T>getExtensions(extensionPointName.getName(), areaInstance);
  }

  public static <T> T[] getExtensions(String extensionPointName, AreaInstance areaInstance) {
    ExtensionsArea area = getArea(areaInstance);
    ExtensionPoint<T> extensionPoint = area.getExtensionPoint(extensionPointName);
    return extensionPoint.getExtensions();
  }

  public static <T, U extends T> U findExtension(ExtensionPointName<T> extensionPointName, Class<U> extClass) {
    for (T t : getExtensions(extensionPointName)) {
      if (extClass.isInstance(t)) {
        //noinspection unchecked
        return (U) t;
      }
    }
    throw new IllegalArgumentException("could not find extension implementation " + extClass);
  }

  public static <T, U extends T> U findExtension(ExtensionPointName<T> extensionPointName, AreaInstance areaInstance, Class<U> extClass) {
    for (T t : getExtensions(extensionPointName, areaInstance)) {
      if (extClass.isInstance(t)) {
        //noinspection unchecked
        return (U) t;
      }
    }
    throw new IllegalArgumentException("could not find extension implementation " + extClass);
  }

  public static void instantiateArea(@NonNls @NotNull String areaClass, AreaInstance areaInstance, AreaInstance parentAreaInstance) {
    if (!ourAreaClass2Configuration.containsKey(areaClass)) {
      throw new IllegalArgumentException("Area class is not registered: " + areaClass);
    }
    if (ourAreaInstance2area.containsKey(areaInstance)) {
      throw new IllegalArgumentException("Area already instantiated for: " + areaInstance);
    }
    ExtensionsArea parentArea = getArea(parentAreaInstance);
    AreaClassConfiguration configuration = ourAreaClass2Configuration.get(areaClass);
    if (!equals(parentArea.getAreaClass(), configuration.getParentClassName())) {
      throw new IllegalArgumentException("Wrong parent area. Expected class: " + configuration.getParentClassName() + " actual class: " + parentArea.getAreaClass());
    }
    ExtensionsAreaImpl area = new ExtensionsAreaImpl(areaClass, areaInstance, parentArea.getPicoContainer(), ourLogger);
    ourAreaInstance2area.put(areaInstance, area);
    ourAreaClass2instances.putValue(areaClass, areaInstance);
    ourAreaInstance2class.put(areaInstance, areaClass);
    AreaListener[] listeners = getAreaListeners();
    for (AreaListener listener : listeners) {
      listener.areaCreated(areaClass, areaInstance);
    }
  }

  private static AreaListener[] getAreaListeners() {
    return getRootArea().getExtensionPoint(AREA_LISTENER_EXTENSION_POINT).getExtensions();
  }

  public static void registerAreaClass(@NonNls String areaClass, @NonNls String parentAreaClass) {
    if (ourAreaClass2Configuration.containsKey(areaClass)) {
      // allow duplicate area class registrations if they are the same - fixing duplicate registration in tests is much more trouble
      AreaClassConfiguration configuration = ourAreaClass2Configuration.get(areaClass);
      if (!equals(configuration.getParentClassName(), parentAreaClass)) {
        throw new RuntimeException("Area class already registered: " + areaClass, ourAreaClass2Configuration.get(areaClass).getCreationPoint());
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

    AreaListener[] listeners = getAreaListeners();
    String areaClass = ourAreaInstance2class.get(areaInstance);
    if (areaClass == null) {
      throw new IllegalArgumentException("Area class is null (area never instantiated?). Instance: " + areaInstance);
    }
    try {
      for (AreaListener listener : listeners) {
        listener.areaDisposing(areaClass, areaInstance);
      }
    } finally {
      ourAreaInstance2area.remove(areaInstance);
      ourAreaClass2instances.removeValue(ourAreaInstance2class.remove(areaInstance), areaInstance);
      ourAreaInstance2class.remove(areaInstance);
    }
  }

  public static AreaInstance[] getAllAreas(String areaClass) {
    Collection<AreaInstance> instances = ourAreaClass2instances.get(areaClass);
    return instances.toArray(new AreaInstance[instances.size()]);
  }

  private static boolean equals(Object object1, Object object2) {
    return object1 == object2 || object1 != null && object2 != null && object1.equals(object2);
  }

  public static void setLogProvider(LogProvider logProvider) {
    ourLogger = logProvider;
  }

  private static class AreaClassConfiguration {
    private final String myClassName;
    private final String myParentClassName;
    private final Throwable myCreationPoint;

    AreaClassConfiguration(String className, String parentClassName) {
      myCreationPoint = new Throwable();
      myClassName = className;
      myParentClassName = parentClassName;
    }

    public Throwable getCreationPoint() {
      return myCreationPoint;
    }

    public String getClassName() {
      return myClassName;
    }

    public String getParentClassName() {
      return myParentClassName;
    }
  }

  public static class SimpleLogProvider implements LogProvider {
    public void error(String message) {
      new Throwable(message).printStackTrace();
    }

    public void error(String message, Throwable t) {
      System.err.println(message);
      t.printStackTrace();
    }

    public void error(Throwable t) {
      t.printStackTrace();
    }

    public void warn(String message) {
      System.err.println(message);
    }

    public void warn(String message, Throwable t) {
      System.err.println(message);
      t.printStackTrace();
    }

    public void warn(Throwable t) {
      t.printStackTrace();
    }
  }
}
