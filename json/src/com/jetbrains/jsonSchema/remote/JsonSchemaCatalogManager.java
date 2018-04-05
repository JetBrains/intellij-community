// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.remote;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileManager;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.impl.JsonCachedValues;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class JsonSchemaCatalogManager {
  private static final String DEFAULT_CATALOG = "http://schemastore.org/api/json/catalog.json";
  @NotNull private final Project myProject;
  @NotNull private final JsonSchemaRemoteContentProvider myRemoteContentProvider;
  @Nullable private VirtualFile myCatalog = null;
  @NotNull private final ConcurrentMap<String, String> myResolvedMappings = ContainerUtil.newConcurrentMap();
  private static final String NO_CACHE = "$_$_WS_NO_CACHE_$_$";
  private static final String EMPTY = "$_$_WS_EMPTY_$_$";
  private static final AtomicBoolean myIsEnabled = new AtomicBoolean(false);

  public JsonSchemaCatalogManager(@NotNull Project project) {
    myProject = project;
    myRemoteContentProvider = new JsonSchemaRemoteContentProvider();
  }

  public void setEnabled(boolean enabled) {
    myIsEnabled.set(enabled);
  }

  public void startUpdates() {
    RemoteFileManager instance = RemoteFileManager.getInstance();
    instance.addRemoteContentProvider(myRemoteContentProvider);
    myCatalog = JsonFileResolver.urlToFile(DEFAULT_CATALOG);
  }

  @Nullable
  public VirtualFile getSchemaFileForFile(@NotNull VirtualFile file) {
    if (!myIsEnabled.get()) return null;

    String name = file.getName();
    if (myResolvedMappings.containsKey(name)) {
      String urlString = myResolvedMappings.get(name);
      if (EMPTY.equals(urlString)) return null;
      return JsonFileResolver.resolveSchemaByReference(file, urlString);
    }

    if (myCatalog != null) {
      String urlString = resolveSchemaFile(file, myCatalog, myProject);
      if (NO_CACHE.equals(urlString)) return null;
      myResolvedMappings.put(name, urlString == null ? EMPTY : urlString);
      return JsonFileResolver.resolveSchemaByReference(file, urlString);
    }

    return null;
  }

  @Nullable
  private static String resolveSchemaFile(@NotNull VirtualFile file, @NotNull VirtualFile catalogFile, @NotNull Project project) {
    JsonFileResolver.startFetchingHttpFileIfNeeded(catalogFile);

    List<Pair<Collection<String>, String>> schemaCatalog = JsonCachedValues.getSchemaCatalog(catalogFile, project);
    if (schemaCatalog == null) return catalogFile instanceof HttpVirtualFile ? NO_CACHE : null;
    String fileName = file.getName();
    for (Pair<Collection<String>, String> maskAndPath: schemaCatalog) {
      if (matches(fileName, maskAndPath.first)) {
        return maskAndPath.second;
      }
    }

    return null;
  }

  private static boolean matches(@NotNull String fileName, @NotNull Collection<String> masks) {
    for (String mask: masks) {
      if (matches(fileName, mask)) return true;
    }
    return false;
  }

  private static boolean matches(@NotNull String fileName, @NotNull String mask) {
    if (mask.equals(fileName)) return true;
    int star = mask.indexOf('*');

    // no star - no match
    if (star == -1) return false;

    // *.foo.json
    if (star == 0 && fileName.startsWith(mask.substring(1))) {
      return true;
    }

    // foobar*
    if (star == mask.length() - 1 && fileName.endsWith(mask.substring(0, mask.length() - 1))) {
      return true;
    }

    String beforeStar = mask.substring(0, star);
    String afterStar = mask.substring(star + 1);

    if (fileName.startsWith(beforeStar) && fileName.endsWith(afterStar)) {
      return true;
    }
    return false;
  }
}
