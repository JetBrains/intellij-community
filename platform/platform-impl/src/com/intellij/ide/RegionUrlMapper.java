// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.JsonReaderEx;
import org.jetbrains.io.JsonUtil;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @see Region
 * @see RegionSettings
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public final class RegionUrlMapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.RegionUrlMapper");

  private static final int CACHE_DATA_EXPIRATION_MIN = 2;
  private static final String CONFIG_URL_DEFAULT = "https://www.jetbrains.com/config/JetBrainsResourceMapping.json";
  private static final Map<Region, String> CONFIG_URL_TABLE = Map.of(
    // augment the table with other regions if needed
    Region.CHINA, "https://www.jetbrains.com.cn/config/JetBrainsResourceMapping.json"
  );
  
  private static final Map<Region, String> OVERRIDE_CONFIG_URL_TABLE = new HashMap<>();  // for testing
  static {
    for (Region reg : Region.values()) {
      String propName = "jb.mapper.configuration.url";
      if (reg != Region.NOT_SET) {
        propName = propName + "." + reg.name().toLowerCase(Locale.ENGLISH);
      }
      String overridden = System.getProperty(propName, "");
      if (!overridden.isBlank()) {
        OVERRIDE_CONFIG_URL_TABLE.put(reg, overridden);
      }
    }
  }

  private static final LoadingCache<Region, List<Pair<String, String>>> ourCache = Caffeine.newBuilder().expireAfterWrite(CACHE_DATA_EXPIRATION_MIN, TimeUnit.MINUTES).build(RegionUrlMapper::loadMappings);

  private RegionUrlMapper() {
  }

  /**
   * Maps the specified resource URL to a corresponding region-specific URL for the region that is configured for the IDE
   * see {@link #mapUrl(String, Region)}
   *
   * IMPORTANT: the method is potentially long-executing; involves network calls
   *
   * @param url the original resource URL
   * @return the possibly adjusted URL that is specific to the currently specified IDE region.
   */
  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  @Contract("null -> null")
  @Nullable
  public static String mapUrl(@Nullable String url) {
    return mapUrl(url, RegionSettings.getRegion());
  }

  /**
   * Maps the specified resource URL to a corresponding region-specific URL
   * IMPORTANT: the method is potentially long-executing; involves network calls
   *
   * @param url the original resource URL
   * @param region the region for which the original url might be adjusted
   * @return the adjusted url, in case the mapping is configured or the original url, if no adjustments are required
   */
  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  @Contract("null, _ -> null")
  @Nullable
  public static String mapUrl(@Nullable String url, @NotNull Region region) {
    if (url != null) {
      for (Pair<String, String> pair : ourCache.get(region)) {
        String pattern = pair.getFirst();
        int entry = Strings.indexOfIgnoreCase(url, pattern, 0);
        if (entry >= 0) {
          String replacement = pair.getSecond();
          return url.substring(0, entry) + replacement + url.substring(entry + pattern.length());
        }
      }
    }
    return url;
  }

  private static List<Pair<String, String>> loadMappings(Region reg) {
    String configUrl = getConfigUrl(reg);
    try {
      List<Pair<String, String>> result = new SmartList<>();
      String json = HttpRequests.request(configUrl).readString();
      for (Map<String, Object> mapping : JsonUtil.<Map<String, Object>>nextList(new JsonReaderEx(json))) {
        if (mapping.size() == 1) {
          Map.Entry<String, Object> entry = mapping.entrySet().iterator().next();
          String pattern = entry.getKey();
          if (!Strings.isEmpty(pattern) && entry.getValue() instanceof String replacement) {
            result.add(Pair.create(pattern, replacement));
          }
        }
      }
      return result;
    }
    catch (Throwable e) {
      LOG.info("Failed to load region-specific url mappings : " + e.getMessage());
    }
    return Collections.emptyList();
  }

  private static @NotNull String getConfigUrl(Region reg) {
    String overridden = OVERRIDE_CONFIG_URL_TABLE.get(reg);
    return overridden != null? overridden : CONFIG_URL_TABLE.getOrDefault(reg, CONFIG_URL_DEFAULT);
  }

}
