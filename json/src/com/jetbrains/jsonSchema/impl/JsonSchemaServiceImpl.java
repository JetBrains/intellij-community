package com.jetbrains.jsonSchema.impl;


import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.idea.RareLogger;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.JsonSchemaMappingsConfigurable;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaImportedProviderMarker;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.Nls;
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

    return wrappers.isEmpty() ? null : (wrappers.size() == 1 ? wrappers.get(0) : new CompositeCodeInsightProviderWithWarning(wrappers));
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
        private String message;
        {
          boolean haveSystemSchemas = false;
          for (JsonSchemaObjectCodeInsightWrapper wrapper : myWrappers) {
            haveSystemSchemas |= !wrapper.isUserSchema();
          }
          boolean withTypes = haveSystemSchemas;
          final List<String> names = new ArrayList<>();
          for (JsonSchemaObjectCodeInsightWrapper wrapper : myWrappers) {
            if (withTypes) {
              names.add((wrapper.isUserSchema() ? "user" : "system") + " schema '" + wrapper.getName() + "'");
            } else {
              names.add(wrapper.getName());
            }
          }
          message = "<html>There are several JSON Schemas mapped to this file: " + StringUtil.join(names, "; ") + "</html>";
        }

        @Override
        public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
          if (element instanceof PsiFile) {
            addFileLevelWarning(element, holder);
          }
          for (JsonSchemaObjectCodeInsightWrapper wrapper : myWrappers) {
            wrapper.getAnnotator().annotate(element, holder);
          }
        }

        private void addFileLevelWarning(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
          final Annotation annotation = holder.createErrorAnnotation(element, message);
          annotation.setFileLevelAnnotation(true);
          annotation.registerFix(new IntentionAction() {
            @Nls
            @NotNull
            @Override
            public String getText() {
              return "Edit JSON Schema Mappings";
            }

            @Nls
            @NotNull
            @Override
            public String getFamilyName() {
              return "JSON Schema";
            }

            @Override
            public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
              return true;
            }

            @Override
            public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
              ShowSettingsUtil.getInstance().editConfigurable(project, new JsonSchemaMappingsConfigurable(project));
              DaemonCodeAnalyzer.getInstance(project).restart(file);
            }

            @Override
            public boolean startInWriteAction() {
              return false;
            }
          });
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
