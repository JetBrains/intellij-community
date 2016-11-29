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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import com.intellij.util.NotNullFunction;
import com.intellij.util.PairConsumer;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.JsonSchemaFileTypeManager;
import com.jetbrains.jsonSchema.JsonSchemaVfsListener;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaImportedProviderMarker;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;

public class JsonSchemaServiceImpl implements JsonSchemaServiceEx {
  private static final Logger LOGGER = Logger.getInstance(JsonSchemaServiceImpl.class);
  private static final Logger RARE_LOGGER = RareLogger.wrap(LOGGER, false);
  public static final Comparator<JsonSchemaFileProvider> FILE_PROVIDER_COMPARATOR = new Comparator<JsonSchemaFileProvider>() {
    @Override
    public int compare(JsonSchemaFileProvider o1, JsonSchemaFileProvider o2) {
      return Integer.compare(o1.getOrder(), o2.getOrder());
    }
  };
  @NotNull
  private final Project myProject;
  private final Object myLock;
  private final Map<VirtualFile, JsonSchemaObjectCodeInsightWrapper> myWrappers = new HashMap<>();
  private final Set<VirtualFile> mySchemaFiles = ContainerUtil.newConcurrentSet();
  private volatile boolean initialized;
  private final JsonSchemaExportedDefinitions myDefinitions;

  public JsonSchemaServiceImpl(@NotNull Project project) {
    myLock = new Object();
    myProject = project;
    myDefinitions = new JsonSchemaExportedDefinitions(this::iterateSchemas);
    ApplicationManager
      .getApplication().getMessageBus().connect(project).subscribe(VirtualFileManager.VFS_CHANGES, new JsonSchemaVfsListener(project, this));
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

  public boolean hasSchema(@Nullable VirtualFile file) {
    CodeInsightProviders wrapper = getWrapper(file);
    return wrapper != null;
  }

  @Override
  public boolean isRegisteredSchemaFile(@NotNull Project project, @NotNull VirtualFile file) {
    if (!initialized) {
      ensureSchemaFiles();
    }
    return mySchemaFiles.contains(file);
  }

  private void ensureSchemaFiles() {
    synchronized (myLock) {
      if (!initialized) {
        for (JsonSchemaFileProvider provider : getProviders()) {
          final VirtualFile schemaFile = provider.getSchemaFile();
          if (schemaFile != null) {
            mySchemaFiles.add(schemaFile);
          }
        }
        initialized = true;
      }
    }
  }

  @Override
  public boolean isSchemaFile(@NotNull VirtualFile file, @NotNull final Consumer<String> errorConsumer) {
    final String text;
    try {
      text = VfsUtilCore.loadText(file);
    }
    catch (IOException e) {
      errorConsumer.consume(e.getMessage());
      return false;
    }
    try {
      return JsonSchemaReader.isJsonSchema(getDefinitions(), file, text, errorConsumer);
    }
    catch (Exception e) {
      reset();
      errorConsumer.consume(e.getMessage());
      return false;
    }
  }

  @NotNull
  private JsonSchemaExportedDefinitions getDefinitions() {
    final JsonSchemaExportedDefinitions definitions;
    synchronized (myLock) {
      definitions = myDefinitions;
    }
    return definitions;
  }

  @Nullable
  @Override
  public DocumentationProvider getDocumentationProvider(@Nullable VirtualFile file) {
    CodeInsightProviders wrapper = getWrapper(file);
    return wrapper != null ? wrapper.getDocumentationProvider() : null;
  }

  @Override
  public void iterateSchemaObjects(VirtualFile file, @NotNull Processor<JsonSchemaObject> consumer) {
    final CodeInsightProviders wrapper = getWrapper(file);
    if (wrapper == null) return;
    wrapper.iterateSchemaObjects(consumer);
  }

  @Nullable
  @Override
  public List<Pair<Boolean, String>> getMatchingSchemaDescriptors(@Nullable VirtualFile file) {
    final List<JsonSchemaObjectCodeInsightWrapper> wrappers = getWrappers(file);
    if (wrappers == null || wrappers.isEmpty()) return null;
    return ContainerUtil.map(wrappers, (NotNullFunction<JsonSchemaObjectCodeInsightWrapper, Pair<Boolean, String>>)
      wrapper -> Pair.create(wrapper.isUserSchema(), wrapper.getName()));
  }

  @Nullable
  private JsonSchemaObjectCodeInsightWrapper createWrapper(@NotNull JsonSchemaFileProvider provider) {
    final JsonSchemaObject resultObject = readObject(provider, getDefinitions());
    if (resultObject == null) return null;
    return new JsonSchemaObjectCodeInsightWrapper(myProject, provider.getName(), provider.getSchemaType(), provider.getSchemaFile(), resultObject);
  }

  private static JsonSchemaObject readObject(@NotNull JsonSchemaFileProvider provider,
                                             @Nullable final JsonSchemaExportedDefinitions definitions) {
    final VirtualFile file = provider.getSchemaFile();
    if (file == null) return null;
    Reader reader = null;
    try {
      //noinspection StaticMethodReferencedViaSubclass
      final String text = VfsUtil.loadText(file);
      reader = new StringReader(text);
      return new JsonSchemaReader(provider.getSchemaFile()).read(reader, definitions);
    }
    catch (Exception e) {
      logException(provider, e);
    } finally {
      if (reader != null) try {
        reader.close();
      }
      catch (IOException e) {
        logException(provider, e);
      }
    }
    return null;
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
    final FileType type = file.getFileType();
    if (type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage().isKindOf(JsonLanguage.INSTANCE)) {
      final List<JsonSchemaObjectCodeInsightWrapper> wrappers = getWrappers(file);
      if (wrappers == null || wrappers.isEmpty()) {
        return null;
      }
      return (wrappers.size() == 1 ? wrappers.get(0) : new CompositeCodeInsightProviderWithWarning(wrappers));
    }
    return null;
  }

  public void iterateSchemas(@NotNull final PairConsumer<VirtualFile, NullableLazyValue<JsonSchemaObject>> consumer) {
    for (JsonSchemaFileProvider provider : getProviders()) {
      consumer.consume(provider.getSchemaFile(),
                       new NullableLazyValue<JsonSchemaObject>() {
                         @Override
                         protected JsonSchemaObject compute() {
                           return readObject(provider, null);
                         }
                       });
    }
  }

  public void dropProviderFromCache(@NotNull final VirtualFile key) {
    synchronized (myLock) {
      final Set<VirtualFile> dirtySet = myDefinitions.dropKey(key);
      final Iterator<VirtualFile> iterator = myWrappers.keySet().iterator();
      while (iterator.hasNext()) {
        final VirtualFile current = iterator.next();
        if (dirtySet.contains(current)) iterator.remove();
      }
    }
  }

  @Nullable
  private List<JsonSchemaObjectCodeInsightWrapper> getWrappers(@Nullable VirtualFile file) {
    if (file == null) return null;
    final List<JsonSchemaObjectCodeInsightWrapper> wrappers = new ArrayList<>();
    synchronized (myLock) {
      final Set<VirtualFile> files = mySchemaFiles.isEmpty() ? new HashSet<>() : null;
      for (JsonSchemaFileProvider provider : getProviders()) {
        final VirtualFile key = provider.getSchemaFile();
        if (files != null) files.add(key);
        if (provider.isAvailable(myProject, file)) {
          JsonSchemaObjectCodeInsightWrapper wrapper = myWrappers.get(key);
          if (wrapper == null) {
            wrapper = createWrapper(provider);
            if (wrapper == null) return null;
            myWrappers.putIfAbsent(key, wrapper);
          }
          wrappers.add(wrapper);
        }
      }
      if (files != null) mySchemaFiles.addAll(files);
    }
    return wrappers;
  }

  private static class CompositeCodeInsightProviderWithWarning implements CodeInsightProviders {
    private final List<JsonSchemaObjectCodeInsightWrapper> myWrappers;
    private final CompletionContributor myContributor;
    private final Annotator myAnnotator;
    private final DocumentationProvider myDocumentationProvider;

    public CompositeCodeInsightProviderWithWarning(List<JsonSchemaObjectCodeInsightWrapper> wrappers) {
      final List<JsonSchemaObjectCodeInsightWrapper> userSchemaWrappers =
        ContainerUtil.filter(wrappers, JsonSchemaObjectCodeInsightWrapper::isUserSchema);
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
          for (JsonSchemaObjectCodeInsightWrapper wrapper : myWrappers) {
            wrapper.getContributor().fillCompletionVariants(parameters, result);
          }
        }
      };
      myAnnotator = new Annotator() {
        @Override
        public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
          for (JsonSchemaObjectCodeInsightWrapper wrapper : myWrappers) {
            wrapper.getAnnotator().annotate(element, holder);
          }
        }
      };
      final List<DocumentationProvider> list = new ArrayList<>();
      for (JsonSchemaObjectCodeInsightWrapper wrapper : myWrappers) {
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

    @Override
    public boolean iterateSchemaObjects(@NotNull Processor<JsonSchemaObject> consumer) {
      for (JsonSchemaObjectCodeInsightWrapper wrapper : myWrappers) {
        if (!wrapper.iterateSchemaObjects(consumer)) return false;
      }
      return true;
    }

    @Override
    public void iterateSchemaFiles(@NotNull PairConsumer<VirtualFile, String> consumer) {
      for (JsonSchemaObjectCodeInsightWrapper wrapper : myWrappers) {
        wrapper.iterateSchemaFiles(consumer);
      }
    }
  }

  @Override
  public boolean checkFileForId(@NotNull final String id, @NotNull final VirtualFile file) {
    return myDefinitions.checkFileForId(id, file);
  }

  @Override
  @Nullable
  public VirtualFile getSchemaFileById(@NotNull String id) {
    return myDefinitions.getSchemaFileById(id);
  }

  @Override
  @Nullable
  public Collection<Pair<VirtualFile, String>> getSchemaFilesByFile(@NotNull final VirtualFile file) {
    final CodeInsightProviders wrapper = getWrapper(file);
    if (wrapper != null) {
      final List<Pair<VirtualFile, String>> result = new ArrayList<>();
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
}
