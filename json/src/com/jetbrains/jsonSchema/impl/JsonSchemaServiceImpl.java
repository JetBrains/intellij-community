package com.jetbrains.jsonSchema.impl;


import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
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
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.JsonSchemaFileTypeManager;
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
import java.util.function.Function;
import java.util.stream.Collectors;

public class JsonSchemaServiceImpl implements JsonSchemaService {
  @NotNull
  private final Project myProject;
  private final MyState myState;
  private final AtomicLong myModificationCount = new AtomicLong(0);
  private final ModificationTracker myModificationTracker;

  public JsonSchemaServiceImpl(@NotNull Project project) {
    myProject = project;
    myState = new MyState(Arrays.stream(getProviderFactories())
                            .map(JsonSchemaProviderFactory::getProviders)
                            .flatMap(List::stream)
                            .collect(Collectors.toList()),
                          () -> Arrays.stream(getProviderFactories())
                            .map(factory -> factory.getProviders(myProject))
                            .flatMap(List::stream)
                            .collect(Collectors.toList()));
    myModificationTracker = () -> myModificationCount.get();
    JsonSchemaVfsListener.startListening(project, this);
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
    myModificationCount.incrementAndGet();
    myState.reset();
    JsonSchemaFileTypeManager.getInstance().reset();
    ApplicationManager.getApplication().invokeLater(() -> WriteAction.run(() -> FileTypeManagerEx.getInstanceEx().fireFileTypesChanged()),
                                                    ModalityState.NON_MODAL, myProject.getDisposed());
  }

  @Override
  @Nullable
  public VirtualFile findSchemaFileByReference(@NotNull String reference, @Nullable VirtualFile referent) {
    final Optional<VirtualFile> optional = myState.getFiles().stream()
      .filter(file -> reference.equals(ReadJsonSchemaFromPsi.readSchemaId(myProject, file)))
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

  @NotNull
  @Override
  public Set<VirtualFile> getSchemaFiles() {
    return myState.getFiles();
  }

  @Nullable
  private JsonSchemaObject readCachedObject(@NotNull VirtualFile schemaFile) {
    final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(schemaFile);
    if (psiFile == null || !(psiFile instanceof JsonFile)) return null;

    final CachedValueProvider<JsonSchemaObject> provider = () -> {
      final JsonObject topLevelValue = ObjectUtils.tryCast(((JsonFile)psiFile).getTopLevelValue(), JsonObject.class);
      if (topLevelValue == null) return null;

      final JsonSchemaObject object = new JsonSchemaReader(topLevelValue).read();
      return CachedValueProvider.Result.create(object, psiFile, myModificationTracker);
    };
    return ReadAction.compute(() -> CachedValuesManager.getCachedValue(psiFile, provider));
  }

  private boolean isProviderAvailable(@NotNull final VirtualFile file, @NotNull JsonSchemaFileProvider provider) {
    final FileType type = file.getFileType();
    final boolean isJson = type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage().isKindOf(JsonLanguage.INSTANCE);
    return (isJson || !SchemaType.userSchema.equals(provider.getSchemaType())) && provider.isAvailable(myProject, file);
  }

  @Nullable
  private static VirtualFile getSchemaFileByRefAsLocalFile(@NotNull String id, @Nullable VirtualFile referent) {
    final String normalizedId = JsonSchemaWalker.normalizeId(id);
    if (FileUtil.isAbsolute(normalizedId) || referent == null) return VfsUtil.findFileByIoFile(new File(normalizedId), false);
    VirtualFile dir = referent.isDirectory() ? referent : referent.getParent();
    if (dir != null && dir.isValid()) {
      final List<String> parts = StringUtil.split(normalizedId.replace("\\", "/"), "/");
      return VfsUtil.findRelativeFile(dir, ArrayUtil.toStringArray(parts));
    }
    return null;
  }

  private static class MyState {
    private final Object myLock = new Object();
    private Map<VirtualFile, JsonSchemaFileProvider> mySchemaProviderMap = ContainerUtil.newHashMap();
    private Collection<JsonSchemaFileProvider> myUnmodifiableProviders = Collections.emptyList();
    private Set<VirtualFile> myUnmodifiableFiles = Collections.emptySet();
    private boolean initialized;
    @NotNull private final Map<VirtualFile, JsonSchemaFileProvider> myApplicationProviders;
    @NotNull private final Factory<List<JsonSchemaFileProvider>> myFactory;

    private MyState(@NotNull final List<JsonSchemaFileProvider> applicationProviders,
                    @NotNull final Factory<List<JsonSchemaFileProvider>> factory) {
      myApplicationProviders = Collections.unmodifiableMap(createFileProviderMap(applicationProviders));
      myFactory = factory;
    }

    public void reset() {
      synchronized (myLock) {
        initialized = false;
        mySchemaProviderMap.clear();
        myUnmodifiableProviders = Collections.emptyList();
        myUnmodifiableFiles = Collections.emptySet();
      }
    }

    @NotNull
    public Collection<JsonSchemaFileProvider> getProviders() {
      synchronized (myLock) {
        ensure();
        return myUnmodifiableProviders;
      }
    }

    @NotNull
    public Set<VirtualFile> getFiles() {
      synchronized (myLock) {
        ensure();
        return myUnmodifiableFiles;
      }
    }

    @Nullable
    public JsonSchemaFileProvider getProvider(@NotNull final VirtualFile file) {
      synchronized (myLock) {
        ensure();
        return mySchemaProviderMap.get(file);
      }
    }

    private void ensure() {
      synchronized (myLock) {
        if (!initialized) {
          mySchemaProviderMap.clear();
          mySchemaProviderMap.putAll(myApplicationProviders);
          mySchemaProviderMap.putAll(createFileProviderMap(myFactory.create()));
          myUnmodifiableFiles = Collections.unmodifiableSet(mySchemaProviderMap.keySet());
          myUnmodifiableProviders = Collections.unmodifiableCollection(mySchemaProviderMap.values());
          initialized = true;
        }
      }
    }

    private static Map<VirtualFile, JsonSchemaFileProvider> createFileProviderMap(@NotNull final List<JsonSchemaFileProvider> list) {
      return list.stream()
        .filter(provider -> provider.getSchemaFile() != null)
        .collect(Collectors.toMap(JsonSchemaFileProvider::getSchemaFile, Function.identity()));
    }
  }
}
