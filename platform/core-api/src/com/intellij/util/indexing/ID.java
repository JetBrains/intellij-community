// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.ide.plugins.PluginUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.io.SimpleStringPersistentEnumerator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Eugene Zhuravlev
 */
public class ID<K, V> extends IndexId<K,V> {
  private static final Logger LOG = Logger.getInstance(ID.class);

  private static volatile SimpleStringPersistentEnumerator ourNameToIdRegistry = new SimpleStringPersistentEnumerator(getEnumFile());

  private final static Map<String, ID<?, ?>> ourIdObjects = new ConcurrentHashMap<>();

  private static final Map<ID<?, ?>, PluginId> ourIdToPluginId = Collections.synchronizedMap(new HashMap<>());
  private static final Map<ID<?, ?>, Throwable> ourIdToRegistrationStackTrace = Collections.synchronizedMap(new HashMap<>());
  static final int MAX_NUMBER_OF_INDICES = Short.MAX_VALUE;

  private volatile int myUniqueId;

  @ApiStatus.Internal
  @NotNull
  private static Path getEnumFile() {
    return PathManager.getIndexRoot().resolve("indices.enum");
  }

  @ApiStatus.Internal
  public static void reloadEnumFile() {
    reloadEnumFile(getEnumFile());
  }

  private static void reloadEnumFile(@NotNull Path enumFile) {
    if (enumFile.equals(ourNameToIdRegistry.getFile())) {
      return;
    }

    SimpleStringPersistentEnumerator newNameToIdRegistry = new SimpleStringPersistentEnumerator(getEnumFile());
    Map<String, Integer> newInvertedState = newNameToIdRegistry.getInvertedState();
    Map<String, Integer> oldInvertedState = ourNameToIdRegistry.getInvertedState();

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

    ourNameToIdRegistry = newNameToIdRegistry;
  }

  private static void reassign(String name, int newId) {
    ID<?, ?> id = ourIdObjects.get(name);
    if (id != null) {
      id.myUniqueId = newId;
    }
  }

  @ApiStatus.Internal
  protected ID(@NotNull String name, @Nullable PluginId pluginId) {
    super(name);
    myUniqueId = stringToId(name);

    ID<?,?> old = ourIdObjects.put(name, this);
    assert old == null : "ID with name '" + name + "' is already registered";

    PluginId oldPluginId = ourIdToPluginId.put(this, pluginId);
    assert oldPluginId == null : "ID with name '" + name + "' is already registered in " + oldPluginId + " but current caller is " + pluginId;

    ourIdToRegistrationStackTrace.put(this, new Throwable());
  }

  private static int stringToId(@NotNull String name) {
    int id = ourNameToIdRegistry.enumerate(name);
    if (id != (short)id) {
      throw new AssertionError("Too many indexes registered");
    }
    return id;
  }

  static void reinitializeDiskStorage() {
    ourNameToIdRegistry.forceDiskSync();
  }

  @NotNull
  public static synchronized <K, V> ID<K, V> create(@NonNls @NotNull String name) {
    PluginId pluginId = getCallerPluginId();
    final ID<K, V> found = findByName(name, true, pluginId);
    return found == null ? new ID<>(name, pluginId) : found;
  }

  @Nullable
  public static <K, V> ID<K, V> findByName(@NotNull String name) {
    return findByName(name, false, null);
  }

  @ApiStatus.Internal
  @Nullable
  protected static <K, V> ID<K, V> findByName(@NotNull String name,
                                              boolean checkCallerPlugin,
                                              @Nullable PluginId requiredPluginId) {
    //noinspection unchecked
    ID<K, V> id = (ID<K, V>)findById(stringToId(name));
    if (checkCallerPlugin && id != null) {
      PluginId actualPluginId = ourIdToPluginId.get(id);

      String actualPluginIdStr = actualPluginId == null ? "IJ Core" : actualPluginId.getIdString();
      String requiredPluginIdStr = requiredPluginId == null ? "IJ Core" : requiredPluginId.getIdString();

      if (!Objects.equals(actualPluginIdStr, requiredPluginIdStr)) {
        Throwable registrationStackTrace = ourIdToRegistrationStackTrace.get(id);
        String message = getInvalidIdAccessMessage(name, actualPluginIdStr, requiredPluginIdStr, registrationStackTrace);
        if (registrationStackTrace != null) {
          throw new AssertionError(message, registrationStackTrace);
        } else {
          throw new AssertionError(message);
        }
      }
    }
    return id;
  }

  @NotNull
  private static String getInvalidIdAccessMessage(@NotNull String name,
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
  @NotNull
  public Throwable getRegistrationTrace() {
    return ourIdToRegistrationStackTrace.get(this);
  }

  public int getUniqueId() {
    return myUniqueId;
  }

  @ApiStatus.Internal
  @Nullable
  public PluginId getPluginId() {
    return ourIdToPluginId.get(this);
  }

  public static ID<?, ?> findById(int id) {
    return ourIdObjects.get(ourNameToIdRegistry.valueOf(id));
  }

  @ApiStatus.Internal
  @Nullable
  protected static PluginId getCallerPluginId() {
    return PluginUtil.getInstance().getCallerPlugin(4);
  }

  @ApiStatus.Internal
  public synchronized static void unloadId(@NotNull ID<?, ?> id) {
    ID<?, ?> oldID = ourIdObjects.remove(id.getName());
    LOG.assertTrue(id.equals(oldID));
    ourIdToPluginId.remove(id);
    ourIdToRegistrationStackTrace.remove(id);
  }
}
