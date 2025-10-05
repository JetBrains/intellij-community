// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import it.unimi.dsi.fastutil.HashCommon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.function.Predicate;

import static com.intellij.util.SystemProperties.getBooleanProperty;

/**
 * Specialized data structure for compact in-memory storage of big amounts of path-like strings
 */
public abstract sealed class InternedPath permits InternedPath.WinInternedPath, InternedPath.UNCWinInternedPath, InternedPath.XInternedPath {
  private static final String PATH_SEPARATORS = ('/' == File.separatorChar) ?
                                                File.separator :
                                                "/" + File.separator;

  private static final boolean USE_CAFFEINE_INTERNER = getBooleanProperty("compiler.InternedPath.USE_CAFFEINE_INTERNER", false);

  private static final PathSegmentsInterner ourNameCache = USE_CAFFEINE_INTERNER ?
                                                           new CaffeineBasedInterner() :
                                                           new SimplestMRUInterner();

  protected final String[] myPath;

  /**
   * @param path assuming absolute local path
   */
  InternedPath(String path) {
    List<String> list = new ArrayList<>();
    StringTokenizer tokenizer = new StringTokenizer(path, PATH_SEPARATORS, false);
    while (tokenizer.hasMoreTokens()) {
      list.add(ourNameCache.intern(tokenizer.nextToken()));
    }
    myPath = list.toArray(String[]::new);
  }

  @NotNull
  public abstract String getValue();

  @NotNull
  public String getName() {
    return myPath.length > 0 ? myPath[myPath.length - 1] : "";
  }

  public boolean contains(Predicate<String> pathElementMatcher) {
    return find(pathElementMatcher) != null;
  }

  @Nullable
  public String find(Predicate<String> pathElementMatcher) {
    for (String elem : myPath) {
      if (pathElementMatcher.test(elem)) {
        return elem;
      }
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    InternedPath other = (InternedPath)o;

    int length = myPath.length;
    if (length != other.myPath.length) {
      return false;
    }

    for (int i = length - 1; i >= 0; i--) {
      if (!Objects.equals(myPath[i], other.myPath[i])) {
        return false;
      }
    }

    return true;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(myPath);
  }

  public static void clearCache() {
    ourNameCache.invalidateAll();
  }

  public static InternedPath create(String path) {
    return path.startsWith("//") || path.startsWith("\\\\")
           ? new UNCWinInternedPath(path.substring(2))
           : path.startsWith("/") ? new XInternedPath(path) : new WinInternedPath(path);
  }

  static final class WinInternedPath extends InternedPath {

    private WinInternedPath(String path) {
      super(path);
    }

    @Override
    public @NotNull String getValue() {
      if (myPath.length == 0) {
        return "";
      }
      if (myPath.length == 1) {
        String name = myPath[0];
        // handle the case of a Windows volume name
        return name.length() == 2 && name.endsWith(":") ? name + '/' : name;
      }

      final StringBuilder buf = new StringBuilder();
      for (CharSequence element : myPath) {
        if (!buf.isEmpty()) {
          buf.append('/');
        }
        buf.append(element);
      }
      return buf.toString();
    }
  }

  static final class UNCWinInternedPath extends InternedPath {

    private UNCWinInternedPath(String path) {
      super(path);
    }

    @Override
    public @NotNull String getValue() {
      if (myPath.length == 0) {
        return "//";
      }
      final StringBuilder buf = new StringBuilder();
      buf.append('/');
      for (CharSequence element : myPath) {
        buf.append('/').append(element);
      }
      return buf.toString();
    }
  }

  static final class XInternedPath extends InternedPath {

    private XInternedPath(String path) {
      super(path);
    }

    @Override
    public @NotNull String getValue() {
      if (myPath.length > 0) {
        final StringBuilder buf = new StringBuilder();
        for (CharSequence element : myPath) {
          buf.append('/').append(element);
        }
        return buf.toString();
      }
      return "/";
    }
  }

  private interface PathSegmentsInterner {
    String intern(@NotNull String segment);

    void invalidateAll();
  }

  /** String interner: very simple but very fast and suitable for concurrent environment */
  private static class SimplestMRUInterner implements PathSegmentsInterner {
    private static final int CACHE_SIZE = (1 << 12); // =4k

    private final String[] cache = new String[CACHE_SIZE];

    @Override
    public String intern(@NotNull String segment) {
      int index = HashCommon.mix(segment.hashCode()) & (CACHE_SIZE - 1);

      String candidate = cache[index];
      if (segment.equals(candidate)) {
        return candidate;
      }

      cache[index] = segment;

      return segment;
    }

    @Override
    public void invalidateAll(){
      Arrays.fill(cache, null);
    }
  }

  private static class CaffeineBasedInterner implements PathSegmentsInterner {
    private static final LoadingCache<@NotNull String, String> cache = Caffeine.newBuilder().maximumSize(2048).build(key -> key);

    @Override
    public String intern(@NotNull String segment) {
      return cache.get(segment);
    }

    @Override
    public void invalidateAll() {
      cache.invalidateAll();
    }
  }
}
