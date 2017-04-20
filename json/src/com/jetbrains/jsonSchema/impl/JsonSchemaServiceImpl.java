package com.jetbrains.jsonSchema.impl;


import com.intellij.idea.RareLogger;
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
import com.jetbrains.jsonSchema.extension.JsonSchemaImportedProviderMarker;
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
  private static final Logger LOGGER = Logger.getInstance(JsonSchemaServiceImpl.class);
  private static final Logger RARE_LOGGER = RareLogger.wrap(LOGGER, false);
  public static final Comparator<JsonSchemaFileProvider> FILE_PROVIDER_COMPARATOR = Comparator.comparingInt(JsonSchemaFileProvider::getOrder);
  @NotNull
  private final Project myProject;
  private final Object myLock;
  private final Map<VirtualFile, JsonSchemaFileProvider> mySchemaFiles = ContainerUtil.newConcurrentMap();
  private volatile boolean initialized;
  private final AtomicLong myModificationCount = new AtomicLong(0);
  private final ModificationTracker myModificationTracker;

  public JsonSchemaServiceImpl(@NotNull Project project) {
    myLock = new Object();
    myProject = project;
    myModificationTracker = () -> myModificationCount.get();
    JsonSchemaVfsListener.startListening(project, this);
    ensureSchemaFiles();
  }

  @NotNull
  protected JsonSchemaProviderFactory[] getProviderFactories() {
    return JsonSchemaProviderFactory.EP_NAME.getExtensions();
  }

  private List<JsonSchemaFileProvider> getProviders() {
    final List<JsonSchemaFileProvider> providers = new ArrayList<>();
    for (JsonSchemaProviderFactory factory : getProviderFactories()) {
      providers.addAll(factory.getProviders(myProject));
    }
    Collections.sort(providers, FILE_PROVIDER_COMPARATOR);
    return providers;
  }


  private void ensureSchemaFiles() {
    synchronized (myLock) {
      if (!initialized) {
        for (JsonSchemaFileProvider provider : getProviders()) {
          final VirtualFile schemaFile = provider.getSchemaFile();
          if (schemaFile != null) {
            mySchemaFiles.put(schemaFile, provider);
          }
        }
        initialized = true;
      }
    }
  }

  @Nullable
  @Override
  public JsonSchemaFileProvider getSchemaProvider(@NotNull VirtualFile schemaFile) {
    synchronized (myLock) {
      ensureSchemaFiles();
      return mySchemaFiles.get(schemaFile);
    }
  }

  private static void logException(@NotNull JsonSchemaFileProvider provider, Exception e) {
    final String message = "Error while processing json schema file: " + e.getMessage();
    if (provider instanceof JsonSchemaImportedProviderMarker) {
      RARE_LOGGER.info(message, e);
    } else {
      LOGGER.error(message, e);
    }
  }

  @Override
  public void reset() {
    myModificationCount.incrementAndGet();
    synchronized (myLock) {
      mySchemaFiles.clear();
      initialized = false;
    }
    JsonSchemaFileTypeManager.getInstance().reset();
    ApplicationManager.getApplication().invokeLater(() -> WriteAction.run(() -> FileTypeManagerEx.getInstanceEx().fireFileTypesChanged()),
                                                    ModalityState.NON_MODAL, myProject.getDisposed());
  }

  private boolean isProviderAvailable(@NotNull final VirtualFile file, @NotNull JsonSchemaFileProvider provider) {
    final FileType type = file.getFileType();
    final boolean isJson = type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage().isKindOf(JsonLanguage.INSTANCE);
    return (isJson || !SchemaType.userSchema.equals(provider.getSchemaType())) && provider.isAvailable(myProject, file);
  }

  @Override
  @Nullable
  public VirtualFile getSchemaFileById(@NotNull String id, @Nullable VirtualFile referent) {
    final Optional<VirtualFile> optional = getSchemaFiles().stream()
      .filter(file -> id.equals(ReadJsonSchemaFromPsi.readSchemaId(myProject, file)))
      .findFirst();
    return optional.orElseGet(() -> getSchemaFileByRefAsLocalFile(id, referent));
  }

  @Nullable
  public static VirtualFile getSchemaFileByRefAsLocalFile(@NotNull String id, @Nullable VirtualFile referent) {
    final String normalizedId = JsonSchemaWalker.normalizeId(id);
    if (FileUtil.isAbsolute(normalizedId) || referent == null) return VfsUtil.findFileByIoFile(new File(normalizedId), false);
    VirtualFile dir = referent.isDirectory() ? referent : referent.getParent();
    if (dir != null && dir.isValid()) {
      final List<String> parts = StringUtil.split(normalizedId.replace("\\", "/"), "/");
      return VfsUtil.findRelativeFile(dir, ArrayUtil.toStringArray(parts));
    }
    return null;
  }

  // todo get available providers from map
  @Override
  @NotNull
  public Collection<VirtualFile> getSchemaFilesForFile(@NotNull final VirtualFile file) {
    return getProviders().stream().filter(provider -> isProviderAvailable(file, provider))
      .map(processor -> processor.getSchemaFile()).collect(Collectors.toList());
  }

  @Nullable
  @Override
  public JsonSchemaObject getSchemaForCodeAssistance(@NotNull final VirtualFile file) {
    final List<JsonSchemaFileProvider> providers =
      getProviders().stream().filter(provider -> isProviderAvailable(file, provider)).collect(Collectors.toList());
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

  @Override
  public Set<VirtualFile> getSchemaFiles() {
    if (!initialized) {
      ensureSchemaFiles();
    }
    // todo consider to keep immutable
    return Collections.unmodifiableSet(mySchemaFiles.keySet());
  }
}
