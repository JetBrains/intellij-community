// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.JsonParseException;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence;
import com.intellij.util.net.PlatformHttpClient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.JsonReaderEx;
import org.jetbrains.io.JsonUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;

/**
 * @see Region
 * @see RegionSettings
 */
@ApiStatus.Internal
public final class RegionUrlMapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.RegionUrlMapper");

  private static final int CACHE_DATA_EXPIRATION_MIN;

  static {
    int expiration;
    try {
      expiration = Integer.parseInt(System.getProperty("ide.region.url.mapping.expiration.timeout", "2"));
    }
    catch (NumberFormatException e) {
      expiration = 2;
    }
    CACHE_DATA_EXPIRATION_MIN = expiration;
  }

  private static final String CONFIG_URL_DEFAULT = "https://www.jetbrains.com/config/JetBrainsResourceMapping.json";
  private static final Map<Region, String> CONFIG_URL_TABLE = Map.of(
    // augment the table with other regions if needed
    Region.CHINA, "https://www.jetbrains.com.cn/config/JetBrainsResourceMapping.json"
  );

  private static final Map<Region, String> OVERRIDE_CONFIG_URL_TABLE = new HashMap<>();  // for testing
  public static final String FORCE_REGION_MAPPINGS_LOAD = "force.region.mappings.load";

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

  private static final AsyncLoadingCache<Region, RegionMapping> ourCache = Caffeine.newBuilder()
    .expireAfterWrite(CACHE_DATA_EXPIRATION_MIN, TimeUnit.MINUTES)
    .buildAsync(RegionUrlMapper::doLoadMappingOrThrow);

  private RegionUrlMapper() { }

  /** @deprecated Use the more explicitly named {@link #tryMapUrlBlocking}, or {@link #tryMapUrl} when calling from a suspending context */
  @Deprecated
  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  public static @NotNull String mapUrl(@NotNull String url) {
    return tryMapUrlBlocking(url);
  }

  /** @deprecated Use the more explicitly named {@link #tryMapUrlBlocking}, or {@link #tryMapUrl} when calling from a suspending context */
  @Deprecated
  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  public static @NotNull String mapUrl(@NotNull String url, @NotNull Region region) {
    return tryMapUrlBlocking(url, region);
  }

  /**
   * @see #tryMapUrlBlocking(String, Region)
   * @see #tryMapUrl(String) when calling from a suspending context, consider using the async version
   * @see RegionSettings
   */
  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  public static @NotNull String tryMapUrlBlocking(@NotNull String url) {
    return tryMapUrlBlocking(url, RegionSettings.getRegion());
  }

  /**
   * Maps the specified resource URL to a corresponding region-specific URL.
   * <p>
   * <b>IMPORTANT</b>: the method is potentially long-executing; involves network calls.
   * Also note that in case the network call fails, this method returns the original URL silently (hence, "try" in its name).
   *
   * @param url the original resource URL
   * @param region the region for which the original url might be adjusted
   * @return the adjusted url in case the mapping is configured, or the original url if no adjustments are required
   * @see #tryMapUrl(String, Region) when calling from a suspending context, consider using the async version
   */
  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  public static @NotNull String tryMapUrlBlocking(@NotNull String url, @NotNull Region region) {
    try {
      return tryMapUrl(url, region).join();
    }
    catch (Throwable e) {
      // tryMapUrl() should have already swallowed any failures, so this shouldn't ever happen
      if (Boolean.valueOf(System.getProperty(FORCE_REGION_MAPPINGS_LOAD)).booleanValue()) {
        throw new RuntimeException(IdeBundle.message("failed.to.load.regional.url.mappings", e.getCause()));
      }
      else {
        LOG.warn("Unexpected exception when mapping region-specific url", e);
        return url;
      }
    }
  }

  /**
   * @see #tryMapUrl(String, Region)
   * @see RegionSettings
   */
  public static @NotNull CompletableFuture<@NotNull String> tryMapUrl(@NotNull String url) {
    return tryMapUrl(url, RegionSettings.getRegion());
  }

  /**
   * Maps the specified resource URL to a corresponding region-specific URL.
   * <p>
   * <b>IMPORTANT</b>: The operation may involve network calls, and if that fails,
   * the returned future resolves to the original URL silently (hence, "try" in its name).
   *
   * @param url the original resource URL
   * @param region the region for which the original url might be adjusted
   * @return a CompletableFuture that resolves to the adjusted url in case the mapping is configured, or the original url otherwise
   */
  public static @NotNull CompletableFuture<@NotNull String> tryMapUrl(@NotNull String url, @NotNull Region region) {
    CompletableFuture<@NotNull RegionMapping> mappingFuture = tryLoadMappingOrEmpty(region);
    return mappingFuture.thenApply(mapping -> mapping.apply(url));
  }

  private static @NotNull CompletableFuture<@NotNull RegionMapping> tryLoadMappingOrEmpty(@NotNull Region region) {
    return loadMapping(region).exceptionally(t -> {
      while (t instanceof CompletionException) {
        t = t.getCause();
      }
      if (t instanceof CancellationException || t instanceof ControlFlowException) {
        LOG.debug("Loading regional URL mappings interrupted (using non-regional URL as fallback): " + t);
      }
      else if (Boolean.valueOf(System.getProperty(FORCE_REGION_MAPPINGS_LOAD)).booleanValue()) {
        LOG.warn("Failed to load regional URL mappings: " + t);
        throw new CompletionException(t);
      }
      else if (t instanceof IOException) {
        // legitimate failure when using the IDE offline; just log it without the stack trace
        LOG.info("Failed to fetch regional URL mappings (using non-regional URL as fallback): " + t);
      }
      else if (t instanceof JsonParseException) {
        LOG.warn("Failed to parse regional URL mappings (using non-regional URL as fallback): " + t);
      }
      else {
        // never suppress errors indicating programmatic bugs or an IDE misconfiguration
        LOG.error("Failed to load regional URL mappings (using non-regional URL as fallback)", t);
      }
      return RegionMapping.empty();
    });
  }

  /**
   * Loads or retrieves a cached value of the {@link RegionMapping} corresponding to the specified region.
   * Loading may fail with an {@link IOException} or {@link JsonParseException}, in which case the resulting future fails with that error.
   *
   * @return a {@link CompletableFuture} that resolves to either the requested mapping once it is loaded, or an error during loading
   */
  public static @NotNull CompletableFuture<@NotNull RegionMapping> loadMapping(@NotNull Region region) {
    return ourCache.get(region);
  }

  private static RegionMapping doLoadMappingOrThrow(Region reg) throws Exception {
    var configUrl = getConfigUrl(reg);
    var client = PlatformHttpClient.client();
    var request = PlatformHttpClient.request(new URI(configUrl));
    var response = PlatformHttpClient.checkResponse(client.send(request, HttpResponse.BodyHandlers.ofString()));
    return RegionMapping.fromJson(response.body());
  }

  private static @NotNull String getConfigUrl(@NotNull Region reg) {
    String overridden = OVERRIDE_CONFIG_URL_TABLE.get(reg);
    return overridden != null ? overridden : CONFIG_URL_TABLE.getOrDefault(reg, CONFIG_URL_DEFAULT);
  }

  /**
   * Mapper for a given region.
   * Represents the contents of the JSON configuration loaded for a particular region
   * and provides the methods for applying the mapping rules found in that configuration.
   */
  @ApiStatus.Internal
  public static final class RegionMapping {
    private final @NotNull List<PatternReplacement> myPatternReplacements;

    private RegionMapping(@NotNull List<PatternReplacement> patternReplacements) { this.myPatternReplacements = patternReplacements; }

    public @NotNull String apply(@NotNull String url) {
      String mappedUrl = applyOrNull(url);
      return mappedUrl != null ? mappedUrl : url;
    }

    public @Nullable String applyOrNull(@NotNull String url) {
      for (PatternReplacement pair : myPatternReplacements) {
        String pattern = pair.pattern();
        int entry = Strings.indexOfIgnoreCase(url, pattern, 0);
        if (entry >= 0) {
          String replacement = pair.replacement();
          return url.substring(0, entry) + replacement + url.substring(entry + pattern.length());
        }
      }
      return null;
    }

    public static @NotNull RegionMapping fromJson(@NotNull String json) throws JsonParseException {
      List<PatternReplacement> result = new SmartList<>();
      for (Map<String, Object> mapping : JsonUtil.<Map<String, Object>>nextList(new JsonReaderEx(json))) {
        if (mapping.size() == 1) {
          Map.Entry<String, Object> entry = mapping.entrySet().iterator().next();
          String pattern = entry.getKey();
          if (!Strings.isEmpty(pattern) && entry.getValue() instanceof String replacement) {
            result.add(new PatternReplacement(pattern, replacement));
          }
        }
      }
      return new RegionMapping(result);
    }

    public static @NotNull RegionMapping empty() {
      return new RegionMapping(Collections.emptyList());
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this || obj instanceof RegionMapping that && Objects.equals(this.myPatternReplacements, that.myPatternReplacements);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myPatternReplacements);
    }

    @Override
    public String toString() { return "RegionMapping[mappings=" + myPatternReplacements + ']'; }

    private record PatternReplacement(@NotNull String pattern, @NotNull String replacement) {
      @Override
      public String toString() { return pattern + " -> " + replacement; }
    }
  }
}
