package com.jetbrains.jsonSchema.impl;


import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.idea.RareLogger;
import com.intellij.json.JsonLanguage;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.CodeInsightProviders;
import com.jetbrains.jsonSchema.JsonSchemaFileTypeManager;
import com.jetbrains.jsonSchema.JsonSchemaVfsListener;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaImportedProviderMarker;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.extension.SchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class JsonSchemaServiceImpl implements JsonSchemaServiceEx {
  private static final Logger LOGGER = Logger.getInstance(JsonSchemaServiceImpl.class);
  private static final Logger RARE_LOGGER = RareLogger.wrap(LOGGER, false);
  public static final Comparator<JsonSchemaFileProvider> FILE_PROVIDER_COMPARATOR = Comparator.comparingInt(JsonSchemaFileProvider::getOrder);
  @NotNull
  private final Project myProject;
  private final Object myLock;
  private final Map<VirtualFile, CodeInsightProviders> myWrappers = new HashMap<>();
  private final Set<VirtualFile> mySchemaFiles = ContainerUtil.newConcurrentSet();
  private volatile boolean initialized;
  private final JsonSchemaExportedDefinitions myDefinitions;

  public JsonSchemaServiceImpl(@NotNull Project project) {
    myLock = new Object();
    myProject = project;
    myDefinitions = new JsonSchemaExportedDefinitions();
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


  @Nullable
  public Annotator getAnnotator(@Nullable VirtualFile file) {
    CodeInsightProviders wrapper = getWrapper(file);
    return wrapper != null ? wrapper.getAnnotator() : null;
  }

  @Nullable
  public CompletionContributor getCompletionContributor(@Nullable VirtualFile file) {
    CodeInsightProviders wrapper = getWrapper(file);
    return wrapper != null ? wrapper.getContributor() : null;
  }

  private void ensureSchemaFiles() {
    synchronized (myLock) {
      if (!initialized) {
        for (JsonSchemaFileProvider provider : getProviders()) {
          final VirtualFile schemaFile = provider.getSchemaFile();
          if (schemaFile != null) {
            mySchemaFiles.add(schemaFile);
            // this will make it refresh
            myDefinitions.dropKey(schemaFile);
            myWrappers.remove(schemaFile);
          }
        }
        initialized = true;
      }
    }
  }

  @Override
  public boolean isSchemaFile(@NotNull VirtualFile file, @NotNull final Consumer<String> errorConsumer) {
    try {
      VfsUtilCore.loadText(file);
    }
    catch (IOException e) {
      errorConsumer.consume(e.getMessage());
      return false;
    }
    try {
      return JsonSchemaReader.isJsonSchema(myProject, file, errorConsumer);
    }
    catch (Exception e) {
      reset();
      errorConsumer.consume(e.getMessage());
      return false;
    }
  }

  @Nullable
  @Override
  public DocumentationProvider getDocumentationProvider(@Nullable VirtualFile file) {
    CodeInsightProviders wrapper = getWrapper(file);
    return wrapper != null ? wrapper.getDocumentationProvider() : null;
  }

  @Override
  public void visitSchemaObject(@NotNull final VirtualFile schemaFile, @NotNull Processor<JsonSchemaObject> consumer) {
    final CodeInsightProviders wrapper = getWrapperBySchemaFile(schemaFile);
    if (wrapper == null) return;
    wrapper.iterateSchemaObjects(consumer);
  }

  @Nullable
  @Override
  public List<Pair<Boolean, String>> getMatchingSchemaDescriptors(@Nullable VirtualFile file) {
    final List<CodeInsightProviders> wrappers = getWrappers(file);
    if (wrappers.isEmpty()) return null;
    return ContainerUtil.map(wrappers, (NotNullFunction<CodeInsightProviders, Pair<Boolean, String>>)
      wrapper -> Pair.create(wrapper.isUserSchema(), wrapper.getName()));
  }

  @Nullable
  private CodeInsightProviders createWrapper(@NotNull JsonSchemaFileProvider provider) {
    final JsonSchemaObject resultObject = readObject(provider);
    if (resultObject == null) return null;
    return provider.proxyCodeInsightProviders(new JsonSchemaObjectCodeInsightWrapper(
      myProject, provider.getName(), provider.getSchemaType(), provider.getSchemaFile(), resultObject));
  }

  private JsonSchemaObject readObject(@NotNull JsonSchemaFileProvider provider) {
    final VirtualFile file = provider.getSchemaFile();
    if (file == null) return null;
    return ReadAction.compute(() -> {
      try {
        final JsonSchemaReader reader = JsonSchemaReader.create(myProject, file);
        if (reader == null) return null;
        final JsonSchemaObject schemaObject = reader.read();
        if (schemaObject.getId() != null) myDefinitions.register(file, schemaObject.getId());
        return schemaObject;
      }
      catch (ProcessCanceledException e) {
        //ignored
      }
      catch (Exception e) {
        logException(provider, e);
      }
      return null;
    });
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
    synchronized (myLock) {
      myWrappers.clear();
      myDefinitions.reset();
      initialized = false;
      mySchemaFiles.clear();
    }
    JsonSchemaFileTypeManager.getInstance().reset();
  }

  @Nullable
  private CodeInsightProviders getWrapper(@Nullable VirtualFile file) {
    if (file == null) return null;
    final List<CodeInsightProviders> wrappers = getWrappers(file);
    if (wrappers.isEmpty()) {
      return null;
    }
    return (wrappers.size() == 1 ? wrappers.get(0) : new CompositeCodeInsightProviderWithWarning(wrappers));
  }

  //! the only point for refreshing json schema caches
  @Override
  public void dropProviderFromCache(@NotNull final VirtualFile schemaFile) {
    synchronized (myLock) {
      myDefinitions.dropKey(schemaFile);
      myWrappers.remove(schemaFile);
    }
  }

  @NotNull
  private List<CodeInsightProviders> getWrappers(@Nullable VirtualFile file) {
    if (file == null) return Collections.emptyList();
    final FileType type = file.getFileType();
    final boolean isJson = type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage().isKindOf(JsonLanguage.INSTANCE);

    final List<CodeInsightProviders> wrappers = new SmartList<>();
    getWrapperSkeletonMethod(provider -> (isJson || !SchemaType.userSchema.equals(provider.getSchemaType())) &&
                                         provider.isAvailable(myProject, file),
                             wrapper -> wrappers.add(wrapper), true);

    return wrappers;
  }

  @Nullable
  private CodeInsightProviders getWrapperBySchemaFile(@NotNull final VirtualFile schemaFile) {
    synchronized (myLock) {
      CodeInsightProviders wrapper = myWrappers.get(schemaFile);
      if (wrapper != null) return wrapper;
    }
    final Ref<CodeInsightProviders> ref = new Ref<>();
    getWrapperSkeletonMethod(provider -> schemaFile.equals(provider.getSchemaFile()), wrapper -> ref.set(wrapper), false);
    return ref.get();
  }

  private void getWrapperSkeletonMethod(@NotNull final Processor<JsonSchemaFileProvider> processor,
                                        @NotNull final Consumer<CodeInsightProviders> consumer,
                                        final boolean multiple) {
    final List<JsonSchemaFileProvider> filtered = getProviders().stream().filter(processor::process).collect(Collectors.toList());
    if (filtered.isEmpty()) return;

    final List<JsonSchemaFileProvider> matchingProviders = new SmartList<>();
    synchronized (myLock) {
      for (JsonSchemaFileProvider provider : filtered) {
        final CodeInsightProviders wrapper = myWrappers.get(provider.getSchemaFile());
        if (wrapper != null) {
          consumer.consume(wrapper);
          if (!multiple) return;
        }
        else {
          matchingProviders.add(provider);
          if (!multiple) break;
        }
      }
    }
    if (matchingProviders.isEmpty()) return;

    final Map<VirtualFile, Pair<CodeInsightProviders, JsonSchemaFileProvider>> created = new HashMap<>();
    for (JsonSchemaFileProvider provider : matchingProviders) {
      // read action taken here => without wrapping lock
      final CodeInsightProviders wrapper = createWrapper(provider);
      if (wrapper != null) created.put(provider.getSchemaFile(), Pair.create(wrapper, provider));
    }

    final List<JsonSchemaFileProvider> providers = getProviders();
    synchronized (myLock) {
      created.forEach((file, pair) -> {
        final CodeInsightProviders wrapper = pair.getFirst();
        final JsonSchemaFileProvider provider = pair.getSecond();
        // check again, providers could have changed
        if (!providers.contains(provider)) return;

        // check again, rules could have changed
        if (processor.process(provider)) {
          myWrappers.putIfAbsent(file, wrapper);
          consumer.consume(wrapper);
        }
      });
    }
  }

  private static class CompositeCodeInsightProviderWithWarning implements CodeInsightProviders {
    private final List<CodeInsightProviders> myWrappers;
    private final CompletionContributor myContributor;
    private final Annotator myAnnotator;
    private final DocumentationProvider myDocumentationProvider;

    public CompositeCodeInsightProviderWithWarning(List<CodeInsightProviders> wrappers) {
      final List<CodeInsightProviders> userSchemaWrappers =
        ContainerUtil.filter(wrappers, CodeInsightProviders::isUserSchema);
      // filter for the case when there are one system schema and one (several) user schemas
      // then do not use provided system schema: user schema will override it (maybe the user updated the version himself)
      // if there are 2 or more system schemas - just go the common way: it is unclear what happened and why
      if (!userSchemaWrappers.isEmpty() && ((userSchemaWrappers.size() + 1) == wrappers.size())) {
        myWrappers = userSchemaWrappers;
      }
      else {
        myWrappers = wrappers;
      }
      myContributor = new CompletionContributor() {
        @Override
        public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
          for (CodeInsightProviders wrapper : myWrappers) {
            wrapper.getContributor().fillCompletionVariants(parameters, result);
          }
        }
      };
      myAnnotator = new Annotator() {
        @Override
        public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
          for (CodeInsightProviders wrapper : myWrappers) {
            wrapper.getAnnotator().annotate(element, holder);
          }
        }
      };
      final List<DocumentationProvider> list = new ArrayList<>();
      for (CodeInsightProviders wrapper : myWrappers) {
        list.add(wrapper.getDocumentationProvider());
      }
      myDocumentationProvider = CompositeDocumentationProvider.wrapProviders(list);
    }

    @NotNull
    @Override
    public CompletionContributor getContributor() {
      return myContributor;
    }

    @NotNull
    @Override
    public Annotator getAnnotator() {
      return myAnnotator;
    }

    @NotNull
    @Override
    public DocumentationProvider getDocumentationProvider() {
      return myDocumentationProvider;
    }

    @NotNull
    @Override
    public String getName() {
      return "Composite";
    }

    @Override
    public boolean isUserSchema() {
      return false;// does not make sense to ask
    }

    @Override
    public boolean iterateSchemaObjects(@NotNull Processor<JsonSchemaObject> consumer) {
      for (CodeInsightProviders wrapper : myWrappers) {
        if (!wrapper.iterateSchemaObjects(consumer)) return false;
      }
      return true;
    }

    @Override
    public void iterateSchemaFiles(@NotNull PairConsumer<VirtualFile, String> consumer) {
      for (CodeInsightProviders wrapper : myWrappers) {
        wrapper.iterateSchemaFiles(consumer);
      }
    }
  }

  @Override
  @Nullable
  public VirtualFile getSchemaFileById(@NotNull String id, @Nullable VirtualFile referent) {
    final VirtualFile schemaFile = myDefinitions.getSchemaFileById(id, this);
    if (schemaFile != null) return schemaFile;
    return getSchemaFileByRefAsLocalFile(id, referent);
  }

  @Nullable
  public static VirtualFile getSchemaFileByRefAsLocalFile(@NotNull String id, @Nullable VirtualFile referent) {
    final String normalizedId = JsonSchemaExportedDefinitions.normalizeId(id);
    if (FileUtil.isAbsolute(normalizedId) || referent == null) return VfsUtil.findFileByIoFile(new File(normalizedId), false);
    VirtualFile dir = referent.isDirectory() ? referent : referent.getParent();
    if (dir != null && dir.isValid()) {
      final List<String> parts = StringUtil.split(normalizedId.replace("\\", "/"), "/");
      return VfsUtil.findRelativeFile(dir, ArrayUtil.toStringArray(parts));
    }
    return null;
  }

  @Override
  @Nullable
  public Collection<Pair<VirtualFile, String>> getSchemaFilesByFile(@NotNull final VirtualFile file) {
    final CodeInsightProviders wrapper = getWrapper(file);
    if (wrapper != null) {
      final List<Pair<VirtualFile, String>> result = new SmartList<>();
      wrapper.iterateSchemaFiles((schemaFile, schemaId) -> result.add(Pair.create(schemaFile, schemaId)));
      return result;
    }
    return null;
  }

  @Override
  public Set<VirtualFile> getSchemaFiles() {
    if (!initialized) {
      ensureSchemaFiles();
    }
    return Collections.unmodifiableSet(mySchemaFiles);
  }

  @Override
  public void refreshSchemaIds(Set<VirtualFile> toRefresh) {
    for (VirtualFile refresh : toRefresh) {
      getWrapperBySchemaFile(refresh);
    }
  }
}
