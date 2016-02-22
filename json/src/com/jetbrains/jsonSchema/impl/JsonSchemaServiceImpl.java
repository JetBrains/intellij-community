package com.jetbrains.jsonSchema.impl;


import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.idea.RareLogger;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaImportedProviderMarker;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

public class JsonSchemaServiceImpl implements JsonSchemaService {
  private static final Logger LOGGER = Logger.getInstance(JsonSchemaServiceImpl.class);
  private static final Logger RARE_LOGGER = RareLogger.wrap(LOGGER, false);
  @Nullable
  private final Project myProject;
  private final ConcurrentMap<JsonSchemaFileProvider, JsonSchemaObjectCodeInsightWrapper> myWrappers = ContainerUtil.newConcurrentMap();

  public JsonSchemaServiceImpl(@Nullable Project project) {
    myProject = project;
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

  @Nullable
  @Override
  public DocumentationProvider getDocumentationProvider(@Nullable VirtualFile file) {
    CodeInsightProviders wrapper = getWrapper(file);
    return wrapper != null ? wrapper.getDocumentationProvider() : null;
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
  private static JsonSchemaObjectCodeInsightWrapper createWrapper(@NotNull JsonSchemaFileProvider provider) {
    Reader reader = provider.getSchemaReader();
    try {
      if (reader != null) {
        JsonSchemaObject resultObject = new JsonSchemaReader().read(reader);
        return new JsonSchemaObjectCodeInsightWrapper(provider.getName(), resultObject).setUserSchema(provider instanceof JsonSchemaImportedProviderMarker);
      }
    }
    catch (Exception e) {
      final String message = "Error while processing json schema file: " + e.getMessage();
      if (provider instanceof JsonSchemaImportedProviderMarker) {
        RARE_LOGGER.info(message, e);
      } else {
        LOGGER.error(message, e);
      }
    }
    return null;
  }

  @Override
  public void reset() {
    myWrappers.clear();
  }

  @Nullable
  private CodeInsightProviders getWrapper(@Nullable VirtualFile file) {
    final List<JsonSchemaObjectCodeInsightWrapper> wrappers = getWrappers(file);
    if (wrappers == null || wrappers.isEmpty()) {
      return null;
    }
    return (wrappers.size() == 1 ? wrappers.get(0) : new CompositeCodeInsightProviderWithWarning(wrappers));
  }

  @Nullable
  private List<JsonSchemaObjectCodeInsightWrapper> getWrappers(@Nullable VirtualFile file) {
    if (file == null) return null;
    final List<JsonSchemaObjectCodeInsightWrapper> wrappers = new ArrayList<>();
    JsonSchemaProviderFactory[] factories = getProviderFactories();
    for (JsonSchemaProviderFactory factory : factories) {
      for (JsonSchemaFileProvider provider : factory.getProviders(myProject)) {
        if (provider.isAvailable(file)) {
          JsonSchemaObjectCodeInsightWrapper wrapper = myWrappers.get(provider);
          if (wrapper == null) {
            JsonSchemaObjectCodeInsightWrapper newWrapper = createWrapper(provider);
            if (newWrapper == null) return null;
            myWrappers.putIfAbsent(provider, newWrapper);
            wrapper = myWrappers.get(provider);
          }
          if (wrapper != null) {
            wrappers.add(wrapper);
          }
        }
      }
    }
    return wrappers;
  }

  private static class CompositeCodeInsightProviderWithWarning implements CodeInsightProviders {
    private final List<JsonSchemaObjectCodeInsightWrapper> myWrappers;
    private CompletionContributor myContributor;
    private Annotator myAnnotator;
    private DocumentationProvider myDocumentationProvider;

    public CompositeCodeInsightProviderWithWarning(List<JsonSchemaObjectCodeInsightWrapper> wrappers) {
      myWrappers = wrappers;
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
  }
}
