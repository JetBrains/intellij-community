// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.ide.plugins.PluginUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.io.SimpleStringPersistentEnumerator;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Eugene Zhuravlev
 */
public class ID<K, V> extends IndexId<K,V> {
  private static final Logger LOG = Logger.getInstance(ID.class);
  private static final Int2ObjectMap<ID<?, ?>> ourRegistry = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

  private static final SimpleStringPersistentEnumerator ourNameToIdRegistry = new SimpleStringPersistentEnumerator(getEnumFile());

  private static final Map<ID<?, ?>, PluginId> ourIdToPluginId = Collections.synchronizedMap(new HashMap<>());
  private static final Map<ID<?, ?>, Throwable> ourIdToRegistrationStackTrace = Collections.synchronizedMap(new HashMap<>());
  static final int MAX_NUMBER_OF_INDICES = Short.MAX_VALUE;

  private final short myUniqueId;

  private static @NotNull Path getEnumFile() {
    return PathManager.getIndexRoot().resolve("indices.enum");
  }

  @ApiStatus.Internal
  protected ID(@NotNull String name, @Nullable PluginId pluginId) {
    super(name);
    myUniqueId = stringToId(name);

    ID<?,?> old = ourRegistry.put(myUniqueId, this);
    assert old == null : "ID with name '" + name + "' is already registered";

    PluginId oldPluginId = ourIdToPluginId.put(this, pluginId);
    assert oldPluginId == null : "ID with name '" + name + "' is already registered in " + oldPluginId + " but current caller is " + pluginId;

    ourIdToRegistrationStackTrace.put(this, new Throwable());
  }

  private static short stringToId(@NotNull String name) {
    return ourNameToIdRegistry.enumerate(name);
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

      String actualPluginIdStr = actualPluginId == null ? "IDEA Core" : actualPluginId.getIdString();
      String requiredPluginIdStr = requiredPluginId == null ? "IDEA Core" : requiredPluginId.getIdString();

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

  @Override
  public final int hashCode() {
    return myUniqueId;
  }

  @Override
  public final boolean equals(Object obj) {
    // IDs are singletons per unique ID. Compare them by object identity.
    return this == obj;
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
    return ourRegistry.get(id);
  }

  @ApiStatus.Internal
  @Nullable
  protected static PluginId getCallerPluginId() {
    return PluginUtil.getInstance().getCallerPlugin(4);
  }

  @ApiStatus.Internal
  public synchronized static void unloadId(@NotNull ID<?, ?> id) {
    ID<?, ?> oldID = ourRegistry.remove(id.getUniqueId());
    LOG.assertTrue(id.equals(oldID));
    ourIdToPluginId.remove(id);
    ourIdToRegistrationStackTrace.remove(id);
  }
}
