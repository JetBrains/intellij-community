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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import com.intellij.util.NotNullFunction;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
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
  @Nullable
  private final Project myProject;
  private final Object myLock;
  private final Map<VirtualFile, JsonSchemaObjectCodeInsightWrapper> myWrappers = new HashMap<>();
  private final Set<VirtualFile> mySchemaFiles = new HashSet<>();
  private final JsonSchemaExportedDefinitions myDefinitions;

  public JsonSchemaServiceImpl(@Nullable Project project) {
    myLock = new Object();
    myProject = project;
    myDefinitions = new JsonSchemaExportedDefinitions(
      new Consumer<PairConsumer<VirtualFile, NullableLazyValue<JsonSchemaObject>>>() {
                                                        @Override
                                                        public void consume(PairConsumer<VirtualFile, NullableLazyValue<JsonSchemaObject>> consumer) {
                                                          iterateSchemas(consumer);
                                                        }
                                                      });
    if (project != null) {
      ApplicationManager
        .getApplication().getMessageBus().connect(project).subscribe(VirtualFileManager.VFS_CHANGES, new JsonSchemaVfsListener(project, this));
    }
  }

  @NotNull
  protected JsonSchemaProviderFactory[] getProviderFactories() {
    return JsonSchemaProviderFactory.EP_NAME.getExtensions();
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
    synchronized (myLock) {
      ensureSchemaFiles(project);
      return mySchemaFiles.contains(file);
    }
  }

  private void ensureSchemaFiles(@NotNull final Project project) {
    synchronized (myLock) {
      if (!mySchemaFiles.isEmpty()) return;
      final JsonSchemaProviderFactory[] factories = getProviderFactories();
      for (JsonSchemaProviderFactory factory : factories) {
        final List<JsonSchemaFileProvider> providers = factory.getProviders(project);
        for (JsonSchemaFileProvider provider : providers) {
          mySchemaFiles.add(provider.getSchemaFile());
        }
      }
    }
  }

  @Override
  public boolean isSchemaFile(@NotNull VirtualFile file, @NotNull final Consumer<String> errorConsumer) {
    final String text;
    try {
      text = VfsUtil.loadText(file);
    }
    catch (IOException e) {
      errorConsumer.consume(e.getMessage());
      return false;
    }
    try {
      return JsonSchemaReader.isJsonSchema(getDefinitions(), file, text, errorConsumer);
    }
    catch (IOException e) {
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

  @Nullable
  @Override
  public Convertor<String, PsiElement> getToPropertyResolver(@Nullable VirtualFile file) {
    CodeInsightProviders wrapper = getWrapper(file);
    return wrapper != null ? wrapper.getToPropertyResolver() : null;
  }

  @Nullable
  @Override
  public List<Pair<Boolean, String>> getMatchingSchemaDescriptors(@Nullable VirtualFile file) {
    final List<JsonSchemaObjectCodeInsightWrapper> wrappers = getWrappers(file);
    if (wrappers == null || wrappers.isEmpty()) return null;
    return ContainerUtil.map(wrappers, new NotNullFunction<JsonSchemaObjectCodeInsightWrapper, Pair<Boolean, String>>() {
      @NotNull
      @Override
      public Pair<Boolean, String> fun(JsonSchemaObjectCodeInsightWrapper wrapper) {
        return Pair.create(wrapper.isUserSchema(), wrapper.getName());
      }
    });
  }

  @Nullable
  private JsonSchemaObjectCodeInsightWrapper createWrapper(@NotNull JsonSchemaFileProvider provider) {
    final JsonSchemaObject resultObject = readObject(provider, getDefinitions());
    if (resultObject == null || myProject == null) return null;
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
      mySchemaFiles.clear();
    }
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
    final JsonSchemaProviderFactory[] factories = getProviderFactories();
    for (JsonSchemaProviderFactory factory : factories) {
      for (JsonSchemaFileProvider provider : factory.getProviders(myProject)) {
        consumer.consume(provider.getSchemaFile(),
                                     new NullableLazyValue<JsonSchemaObject>() {
                                       @Override
                                       protected JsonSchemaObject compute() {
                                         return readObject(provider, null);
                                       }
                                     });
      }
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
    if (file == null || myProject == null) return null;
    final List<JsonSchemaObjectCodeInsightWrapper> wrappers = new ArrayList<>();
    JsonSchemaProviderFactory[] factories = getProviderFactories();
    synchronized (myLock) {
      final Set<VirtualFile> files = mySchemaFiles.isEmpty() ? new HashSet<>() : null;
      for (JsonSchemaProviderFactory factory : factories) {
        for (JsonSchemaFileProvider provider : factory.getProviders(myProject)) {
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
      }
      if (files != null) mySchemaFiles.addAll(files);
    }
    return wrappers;
  }

  private static class CompositeCodeInsightProviderWithWarning implements CodeInsightProviders {
    private final List<JsonSchemaObjectCodeInsightWrapper> myWrappers;
    private CompletionContributor myContributor;
    private Annotator myAnnotator;
    private DocumentationProvider myDocumentationProvider;

    public CompositeCodeInsightProviderWithWarning(List<JsonSchemaObjectCodeInsightWrapper> wrappers) {
      final List<JsonSchemaObjectCodeInsightWrapper> userSchemaWrappers =
        ContainerUtil.filter(wrappers, new Condition<JsonSchemaObjectCodeInsightWrapper>() {
          @Override
          public boolean value(JsonSchemaObjectCodeInsightWrapper wrapper) {
            return wrapper.isUserSchema();
          }
        });
      // filter for the case when there are one system schema and one (several) user schemas
      // then do not use provided system schema: user schema will override it (maybe the user updated the version himself)
      // if there are 2 or more system schemas - just go the common way: it is unclear what happened and why
      if (!userSchemaWrappers.isEmpty() && ((userSchemaWrappers.size() + 1) == wrappers.size())) {
        myWrappers = userSchemaWrappers;
      } else {
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

    @NotNull
    @Override
    public Convertor<String, PsiElement> getToPropertyResolver() {
      return new Convertor<String, PsiElement>() {
        @Override
        public PsiElement convert(String key) {
          for (JsonSchemaObjectCodeInsightWrapper wrapper : myWrappers) {
            PsiElement element = wrapper.getToPropertyResolver().convert(key);
            if (element != null) return element;
          }
          return null;
        }
      };
    }
  }

  @Override
  public boolean checkFileForId(@NotNull final String id, @NotNull final VirtualFile file) {
    return myDefinitions.checkFileForId(id, file);
  }
}
