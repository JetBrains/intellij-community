// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.Java11Shim;
import com.intellij.util.io.SimpleStringPersistentEnumerator;
import org.jetbrains.annotations.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.util.containers.UtilKt.with;
import static com.intellij.util.containers.UtilKt.without;

/**
 * @author Eugene Zhuravlev
 */
public class ID<K, V> extends IndexId<K,V> {
  private static final Logger LOG = Logger.getInstance(ID.class);
  private static final PluginId CORE_PLUGIN_ID = PluginId.getId("com.intellij");

  @ApiStatus.Internal
  public static final String INDICES_ENUM_FILE = "indices.enum";

  private static volatile SimpleStringPersistentEnumerator nameToIdRegistry = new SimpleStringPersistentEnumerator(getEnumFile());

  private static final Map<String, ID<?, ?>> idObjects = new ConcurrentHashMap<>();

  private static final Object lock = new Object();
  private static volatile Map<@NotNull ID<?, ?>, @NotNull PluginId> idToPluginId = Java11Shim.INSTANCE.mapOf();
  private static volatile Map<@NotNull ID<?, ?>, @NotNull Throwable> idToRegistrationStackTrace = Java11Shim.INSTANCE.mapOf();
  @ApiStatus.Internal
  public static final int MAX_NUMBER_OF_INDICES = Short.MAX_VALUE;

  private volatile int uniqueId;

  @ApiStatus.Internal
  private static @NotNull Path getEnumFile() {
    return PathManager.getIndexRoot().resolve(INDICES_ENUM_FILE);
  }

  @ApiStatus.Internal
  public static void reloadEnumFile() {
    reloadEnumFile(getEnumFile());
  }

  //RC: method should probably be synchronized, since it uses current value .ourNameToIdRegistry
  //    while building a new state, and this is unsafe if whole method could be called concurrently,
  //    so that old value could be changed along the way. Right now this is 'safe' since method is
  //    called only while shared index initialization, but...
  private static void reloadEnumFile(@NotNull Path enumFile) {
    if (Files.exists(enumFile) && enumFile.equals(nameToIdRegistry.getFile())) {
      return;
    }

    SimpleStringPersistentEnumerator newNameToIdRegistry = new SimpleStringPersistentEnumerator(getEnumFile());
    Map<String, Integer> newInvertedState = newNameToIdRegistry.getInvertedState();
    Map<String, Integer> oldInvertedState = nameToIdRegistry.getInvertedState();

    oldInvertedState.forEach((oldKey, oldId) -> {
      Integer newId = newInvertedState.get(oldKey);

      if (newId == null) {
        int createdId = newNameToIdRegistry.enumerate(oldKey);
        if (createdId != oldId) {
          reassign(oldKey, createdId);
        }
      }
      else if (oldId.intValue() != newId.intValue()) {
        reassign(oldKey, newId);
      }
    });

    nameToIdRegistry = newNameToIdRegistry;
  }

  private static void reassign(String name, int newId) {
    ID<?, ?> id = idObjects.get(name);
    if (id != null) {
      id.uniqueId = newId;
    }
  }

  @ApiStatus.Internal
  protected ID(@NotNull String name, @Nullable PluginId pluginId) {
    super(name);
    uniqueId = stringToId(name);

    ID<?,?> old = idObjects.put(name, this);
    assert old == null : "ID with name '" + name + "' is already registered";

    synchronized (lock) {
      PluginId oldPluginId = idToPluginId.get(this);
      assert oldPluginId == null : "ID with name '" + name +
                                   "' is already registered in " + oldPluginId +
                                   " but current caller is " + pluginId;

      //noinspection AssignmentToStaticFieldFromInstanceMethod
      idToPluginId = with(idToPluginId, this, pluginId == null ? CORE_PLUGIN_ID : pluginId);
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      idToRegistrationStackTrace = with(idToRegistrationStackTrace, this, new Throwable());
    }
  }

  private static int stringToId(@NotNull String name) {
    int id = nameToIdRegistry.enumerate(name);
    if (id != (short)id) {
      throw new AssertionError("Too many indexes registered");
    }
    return id;
  }

  @ApiStatus.Internal
  public static void reinitializeDiskStorage() {
    nameToIdRegistry.forceDiskSync();
  }

  public static @NotNull <K, V> ID<K, V> create(@NonNls @NotNull String name) {
    PluginId pluginId = getCallerPluginId();
    synchronized (lock) {
      ID<K, V> found = findByName(name, true, pluginId);
      return found == null ? new ID<>(name, pluginId) : found;
    }
  }

  public static @Nullable <K, V> ID<K, V> findByName(@NotNull String name) {
    return findByName(name, false, null);
  }

  @ApiStatus.Internal
  protected static @Nullable <K, V> ID<K, V> findByName(@NotNull String name,
                                                        boolean checkCallerPlugin,
                                                        @Nullable PluginId requiredPluginId) {
    //noinspection unchecked
    ID<K, V> id = (ID<K, V>)findById(stringToId(name));
    if (checkCallerPlugin && id != null) {
      PluginId actualPluginId = idToPluginId.get(id);

      String actualPluginIdStr = actualPluginId == null ? "" : actualPluginId.getIdString();
      String requiredPluginIdStr = requiredPluginId == null ? "" : requiredPluginId.getIdString();

      if (!Objects.equals(actualPluginIdStr, requiredPluginIdStr)) {
        Throwable registrationStackTrace = idToRegistrationStackTrace.get(id);
        String message = getInvalidIdAccessMessage(name, actualPluginIdStr, requiredPluginIdStr, registrationStackTrace);
        if (registrationStackTrace == null) {
          throw new AssertionError(message);
        }
        else {
          throw new AssertionError(message, registrationStackTrace);
        }
      }
    }
    return id;
  }

  private static @NotNull String getInvalidIdAccessMessage(@NotNull String name,
                                                           @Nullable String actualPluginIdStr,
                                                           @Nullable String requiredPluginIdStr,
                                                           @Nullable Throwable registrationStackTrace) {
    return "ID with name '" + name +
           "' requested for plugin " + requiredPluginIdStr +
           " but registered for " + actualPluginIdStr + " plugin. " +
           "Please use an instance field to access corresponding ID." +
           (registrationStackTrace == null ? " Registration stack trace: " : "");
  }

  @ApiStatus.Internal
  public static @Unmodifiable Collection<ID<?, ?>> getRegisteredIds() {
    return idToPluginId.keySet();
  }

  @ApiStatus.Internal
  public @NotNull Throwable getRegistrationTrace() {
    return idToRegistrationStackTrace.get(this);
  }

  @ApiStatus.Internal
  public int getUniqueId() {
    return uniqueId;
  }

  @ApiStatus.Internal
  public @Nullable PluginId getPluginId() {
    return idToPluginId.get(this);
  }

  @ApiStatus.Internal
  public static ID<?, ?> findById(int id) {
    String key = nameToIdRegistry.valueOf(id);
    return key == null ? null : idObjects.get(key);
  }

  @ApiStatus.Internal
  public static void unloadId(@NotNull ID<?, ?> id) {
    String name = id.getName();
    synchronized (lock) {
      ID<?, ?> oldID = idObjects.remove(name);
      LOG.assertTrue(id.equals(oldID), "Failed to unload: " + name);
      idToPluginId = without(idToPluginId, id);
      idToRegistrationStackTrace = without(idToRegistrationStackTrace, id);
    }
  }

  @ApiStatus.Internal
  protected static @Nullable PluginId getCallerPluginId() {
    Class<?> aClass = Java11Shim.INSTANCE.getCallerClass(3);
    if (aClass == null) {
      return null;
    }
    ClassLoader loader = aClass.getClassLoader();
    if (!(loader instanceof PluginAwareClassLoader)) {
      return null;
    }
    return ((PluginAwareClassLoader)loader).getPluginId();
  }
}
