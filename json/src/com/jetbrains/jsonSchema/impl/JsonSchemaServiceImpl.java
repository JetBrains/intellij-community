// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicClearableLazyValue;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.jetbrains.jsonSchema.JsonSchemaVfsListener;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class JsonSchemaServiceImpl implements JsonSchemaService {
  @NotNull
  private final Project myProject;
  private final MyState myState;
  private final AtomicLong myModificationCount = new AtomicLong(0);
  private final AtomicLong myAnyChangeCount = new AtomicLong(0);
  private final ModificationTracker myModificationTracker;
  private final ModificationTracker myAnySchemaChangeTracker;

  public JsonSchemaServiceImpl(@NotNull Project project) {
    myProject = project;
    myState = new MyState(() -> getProvidersFromFactories());
    myModificationTracker = () -> myModificationCount.get();
    myAnySchemaChangeTracker = () -> myAnyChangeCount.get();
    project.getMessageBus().connect().subscribe(JsonSchemaVfsListener.JSON_SCHEMA_CHANGED, myAnyChangeCount::incrementAndGet);
    JsonSchemaVfsListener.startListening(project, this);
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
    myAnyChangeCount.incrementAndGet();
    myState.reset();
    ApplicationManager.getApplication().invokeLater(() -> WriteAction.run(() -> FileTypeManagerEx.getInstanceEx().fireFileTypesChanged()),
                                                    ModalityState.NON_MODAL, myProject.getDisposed());
  }

  @Override
  @Nullable
  public VirtualFile findSchemaFileByReference(@NotNull String reference, @Nullable VirtualFile referent) {
    final Optional<VirtualFile> optional = myState.getFiles().stream()
      .filter(file -> reference.equals(JsonSchemaReader.readSchemaId(myProject, file)))
      .findFirst();
    return optional.orElseGet(() -> getSchemaFileByRefAsLocalFile(reference, referent));
  }

  @Override
  @NotNull
  public Collection<VirtualFile> getSchemaFilesForFile(@NotNull final VirtualFile file) {
    return myState.getProviders().stream().filter(provider -> isProviderAvailable(file, provider))
      .map(processor -> processor.getSchemaFile()).collect(Collectors.toList());
  }

  @Nullable
  @Override
  public JsonSchemaObject getSchemaObject(@NotNull final VirtualFile file) {
    final List<JsonSchemaFileProvider> providers =
      myState.getProviders().stream().filter(provider -> isProviderAvailable(file, provider)).collect(Collectors.toList());
    if (providers.isEmpty() || providers.size() > 2) return null;

    final JsonSchemaFileProvider selected;
    if (providers.size() > 1) {
      final Optional<JsonSchemaFileProvider> userSchema =
        providers.stream().filter(provider -> SchemaType.userSchema.equals(provider.getSchemaType())).findFirst();
      if (!userSchema.isPresent()) return null;
      selected = userSchema.get();
    } else selected = providers.get(0);
    if (selected.getSchemaFile() == null) return null;

    return readCachedObject(selected.getSchemaFile());
  }

  @Nullable
  @Override
  public JsonSchemaObject getSchemaObjectForSchemaFile(@NotNull VirtualFile schemaFile) {
    return readCachedObject(schemaFile);
  }

  @Override
  public boolean isSchemaFile(@NotNull VirtualFile file) {
    return myState.getFiles().contains(file);
  }

  @Nullable
  private JsonSchemaObject readCachedObject(@NotNull VirtualFile schemaFile) {
    final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(schemaFile);
    if (!(psiFile instanceof JsonFile)) return null;

    final CachedValueProvider<JsonSchemaObject> provider = () -> {
      final JsonObject topLevelValue = ObjectUtils.tryCast(((JsonFile)psiFile).getTopLevelValue(), JsonObject.class);
      final JsonSchemaObject object = topLevelValue == null ? null : new JsonSchemaReader().read(topLevelValue);
      return CachedValueProvider.Result.create(object, psiFile, myModificationTracker);
    };
    return ReadAction.compute(() -> CachedValuesManager.getCachedValue(psiFile, provider));
  }

  private static boolean isProviderAvailable(@NotNull final VirtualFile file, @NotNull JsonSchemaFileProvider provider) {
    final FileType type = file.getFileType();
    final boolean isJson = type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage().isKindOf(JsonLanguage.INSTANCE);
    return (isJson || !SchemaType.userSchema.equals(provider.getSchemaType())) && provider.isAvailable(file);
  }

  @Nullable
  private static VirtualFile getSchemaFileByRefAsLocalFile(@NotNull String id, @Nullable VirtualFile referent) {
    final String normalizedId = JsonSchemaService.normalizeId(id);
    if (FileUtil.isAbsolute(normalizedId) || referent == null) return VfsUtil.findFileByIoFile(new File(normalizedId), false);
    VirtualFile dir = referent.isDirectory() ? referent : referent.getParent();
    if (dir != null && dir.isValid()) {
      final List<String> parts = StringUtil.split(normalizedId.replace("\\", "/"), "/");
      return VfsUtil.findRelativeFile(dir, ArrayUtil.toStringArray(parts));
    }
    return null;
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
