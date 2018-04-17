// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicClearableLazyValue;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.JsonSchemaVfsListener;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class JsonSchemaServiceImpl implements JsonSchemaService {
  @NotNull private final Project myProject;
  @NotNull private final MyState myState;
  private final AtomicLong myAnyChangeCount = new AtomicLong(0);
  private final ModificationTracker myAnySchemaChangeTracker;

  @NotNull private final JsonSchemaCatalogManager myCatalogManager;

  public JsonSchemaServiceImpl(@NotNull Project project) {
    myProject = project;
    myState = new MyState(() -> getProvidersFromFactories());
    myAnySchemaChangeTracker = () -> myAnyChangeCount.get();
    myCatalogManager = new JsonSchemaCatalogManager(myProject);

    project.getMessageBus().connect().subscribe(JsonSchemaVfsListener.JSON_SCHEMA_CHANGED, myAnyChangeCount::incrementAndGet);
    JsonSchemaVfsListener.startListening(project, this);
    myCatalogManager.startUpdates();
  }

  @Override
  public ModificationTracker getAnySchemaChangeTracker() {
    return myAnySchemaChangeTracker;
  }

  private List<JsonSchemaFileProvider> getProvidersFromFactories() {
    List<JsonSchemaFileProvider> providers = new ArrayList<>();
    for (JsonSchemaProviderFactory factory : getProviderFactories()) {
      try {
        providers.addAll(factory.getProviders(myProject));
      }
      catch (Exception e) {
        Logger.getInstance(JsonSchemaService.class).error(e);
      }
    }
    return providers;
  }

  @NotNull
  protected JsonSchemaProviderFactory[] getProviderFactories() {
    return JsonSchemaProviderFactory.EP_NAME.getExtensions();
  }

  @Nullable
  @Override
  public JsonSchemaFileProvider getSchemaProvider(@NotNull VirtualFile schemaFile) {
    return myState.getProvider(schemaFile);
  }

  @Override
  public void reset() {
    myState.reset();
    myAnyChangeCount.incrementAndGet();
    DaemonCodeAnalyzer.getInstance(myProject).restart();
  }

  @Override
  @Nullable
  public VirtualFile findSchemaFileByReference(@NotNull String reference, @Nullable VirtualFile referent) {
    final Optional<VirtualFile> optional = myState.getFiles().stream()
      .filter(file -> reference.equals(JsonCachedValues.getSchemaId(file, myProject)))
      .findFirst();
    return optional.orElseGet(() -> JsonFileResolver.resolveSchemaByReference(referent, JsonSchemaService.normalizeId(reference)));
  }

  @Override
  @NotNull
  public Collection<VirtualFile> getSchemaFilesForFile(@NotNull final VirtualFile file) {
    List<VirtualFile> files = myState.getProviders().stream().filter(provider -> isProviderAvailable(file, provider))
                                       .map(processor -> processor.getSchemaFile()).collect(Collectors.toList());
    if (files.size() > 0) {
      return files;
    }

    return ContainerUtil.createMaybeSingletonList(resolveSchemaFromOtherSources(file));
  }

  @Nullable
  @Override
  public JsonSchemaObject getSchemaObject(@NotNull final VirtualFile file) {
    List<JsonSchemaFileProvider> providers =
      myState.getProviders().stream().filter(provider -> isProviderAvailable(file, provider)).collect(Collectors.toList());

    if (providers.isEmpty()) {
      VirtualFile virtualFile = resolveSchemaFromOtherSources(file);
      return virtualFile == null ? null : JsonCachedValues.getSchemaObject(virtualFile, myProject);
    }

    if (providers.size() > 2) return null;

    final JsonSchemaFileProvider selected;
    if (providers.size() > 1) {
      final Optional<JsonSchemaFileProvider> userSchema =
        providers.stream().filter(provider -> SchemaType.userSchema.equals(provider.getSchemaType())).findFirst();
      if (!userSchema.isPresent()) return null;
      selected = userSchema.get();
    } else selected = providers.get(0);
    if (selected.getSchemaFile() == null) return null;

    return JsonCachedValues.getSchemaObject(selected.getSchemaFile(), myProject);
  }

  @Nullable
  @Override
  public JsonSchemaObject getSchemaObjectForSchemaFile(@NotNull VirtualFile schemaFile) {
    return JsonCachedValues.getSchemaObject(schemaFile, myProject);
  }

  @NotNull
  public JsonSchemaCatalogManager getCatalogManager() {
    return myCatalogManager;
  }

  @Override
  public boolean isSchemaFile(@NotNull VirtualFile file) {
    return myState.getFiles().contains(file) || hasSchemaSchema(file);
  }

  @Override
  public JsonSchemaVersion getSchemaVersion(@NotNull VirtualFile file) {
    if (myState.getFiles().contains(file)) {
      JsonSchemaFileProvider provider = myState.getProvider(file);
      if (provider != null) {
        return provider.getSchemaVersion();
      }
    }

    return getSchemaVersionFromSchemaUrl(file);
  }

  @Nullable
  private JsonSchemaVersion getSchemaVersionFromSchemaUrl(@NotNull VirtualFile file) {
    Ref<String> res = Ref.create(null);
    //noinspection CodeBlock2Expr
    ApplicationManager.getApplication().runReadAction(() -> {
      res.set(JsonCachedValues.getSchemaUrlFromSchemaProperty(file, myProject));
    });
    if (res.isNull()) return null;
    return JsonSchemaVersion.byId(res.get());
  }

  private boolean hasSchemaSchema(VirtualFile file) {
    return getSchemaVersionFromSchemaUrl(file) != null;
  }

  private static boolean isProviderAvailable(@NotNull final VirtualFile file, @NotNull JsonSchemaFileProvider provider) {
    final FileType type = file.getFileType();
    final boolean isJson = type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage().isKindOf(JsonLanguage.INSTANCE);
    return (isJson || !SchemaType.userSchema.equals(provider.getSchemaType())) && provider.isAvailable(file);
  }

  @Nullable
  private VirtualFile resolveSchemaFromOtherSources(@NotNull VirtualFile file) {
    VirtualFile virtualFile = JsonCachedValues.getSchemaFileFromSchemaProperty(file, myProject);
    if (virtualFile != null) return virtualFile;
    return myCatalogManager.getSchemaFileForFile(file);
  }

  private static class MyState {
    @NotNull private final Factory<List<JsonSchemaFileProvider>> myFactory;
    @NotNull private final AtomicClearableLazyValue<Map<VirtualFile, JsonSchemaFileProvider>> myData;

    private MyState(@NotNull final Factory<List<JsonSchemaFileProvider>> factory) {
      myFactory = factory;
      myData = new AtomicClearableLazyValue<Map<VirtualFile, JsonSchemaFileProvider>>() {
        @NotNull
        @Override
        public Map<VirtualFile, JsonSchemaFileProvider> compute() {
          return Collections.unmodifiableMap(createFileProviderMap(myFactory.create()));
        }
      };
    }

    public void reset() {
      myData.drop();
    }

    @NotNull
    public Collection<JsonSchemaFileProvider> getProviders() {
      return myData.getValue().values();
    }

    @NotNull
    public Set<VirtualFile> getFiles() {
      return myData.getValue().keySet();
    }

    @Nullable
    public JsonSchemaFileProvider getProvider(@NotNull final VirtualFile file) {
      return myData.getValue().get(file);
    }

    private static Map<VirtualFile, JsonSchemaFileProvider> createFileProviderMap(@NotNull final List<JsonSchemaFileProvider> list) {
      // if there are different providers with the same schema files,
      // stream API does not allow to collect same keys with Collectors.toMap(): throws duplicate key
      final Map<VirtualFile, JsonSchemaFileProvider> map = new HashMap<>();
      list.stream().filter(provider -> provider.getSchemaFile() != null)
        .forEach(provider -> map.put(provider.getSchemaFile(), provider));
      return map;
    }
  }
}
