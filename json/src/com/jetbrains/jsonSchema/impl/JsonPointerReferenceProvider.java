// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonFileType;
import com.intellij.json.pointer.JsonPointerResolver;
import com.intellij.json.psi.*;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileInfoManager;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static com.jetbrains.jsonSchema.JsonPointerUtil.*;
import static com.jetbrains.jsonSchema.remote.JsonFileResolver.isHttpPath;

public final class JsonPointerReferenceProvider extends PsiReferenceProvider {
  private final boolean myIsSchemaProperty;

  public JsonPointerReferenceProvider(boolean isSchemaProperty) {
    myIsSchemaProperty = isSchemaProperty;
  }

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    if (!(element instanceof JsonStringLiteral)) return PsiReference.EMPTY_ARRAY;
    List<PsiReference> refs = new ArrayList<>();

    List<Pair<TextRange, String>> fragments = ((JsonStringLiteral)element).getTextFragments();
    if (fragments.size() != 1) return PsiReference.EMPTY_ARRAY;
    Pair<TextRange, String> fragment = fragments.get(0);
    String originalText = element.getText();
    int hash = originalText.indexOf('#');
    final JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter splitter = new JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter(fragment.second);
    String id = splitter.getSchemaId();
    if (id != null) {
      if (id.startsWith("#")) {
        refs.add(new JsonSchemaIdReference((JsonValue)element, id));
      }
      else {
        addFileOrWebReferences(element, refs, hash, id);
      }
    }
    if (!myIsSchemaProperty) {
      String relativePath = normalizeSlashes(normalizeId(splitter.getRelativePath()));
      if (!StringUtil.isEmpty(relativePath)) {
        List<String> parts1 = split(relativePath);
        String[] strings = ContainerUtil.toArray(parts1, String[]::new);
        List<String> parts2 = split(normalizeSlashes(originalText.substring(hash + 1)));
        if (strings.length == parts2.size()) {
          int start = hash + 2;
          for (int i = 0; i < parts2.size(); i++) {
            int length = parts2.get(i).length();
            if (i == parts2.size() - 1) length--;
            if (length <= 0) break;
            refs.add(new JsonPointerReference((JsonValue)element, new TextRange(start, start + length),
                                              (id == null ? "" : id) + "#/" + StringUtil.join(strings, 0, i + 1, "/")));
            start += length + 1;
          }
        }
      }
    }
    return refs.isEmpty() ? PsiReference.EMPTY_ARRAY : ContainerUtil.toArray(refs, PsiReference[]::new);
  }

  private void addFileOrWebReferences(@NotNull PsiElement element, List<PsiReference> refs, int hashIndex, String id) {
    if (isHttpPath(id)) {
      refs.add(new WebReference(element, new TextRange(1, hashIndex >= 0 ? hashIndex : id.length() + 1), id));
      return;
    }

    boolean isCompletion = id.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED);

    ContainerUtil.addAll(refs, new FileReferenceSet(id, element, 1, null, true,
                                                    true, new JsonFileType[]{JsonFileType.INSTANCE}) {
      @Override
      public boolean isEmptyPathAllowed() {
        return true;
      }

      @Override
      protected boolean isSoft() {
        return true;
      }

      @Override
      public FileReference createFileReference(TextRange range, int index, String text) {
        if (hashIndex != -1 && range.getStartOffset() >= hashIndex) return null;
        if (hashIndex != -1 && range.getEndOffset() > hashIndex) {
          range = new TextRange(range.getStartOffset(), hashIndex);
          text = text.substring(0, text.indexOf('#'));
        }
        return new FileReference(this, range, index, text) {
          @Override
          protected Object createLookupItem(PsiElement candidate) {
            return FileInfoManager.getFileLookupItem(candidate);
          }

          @Override
          public Object @NotNull [] getVariants() {
            final Object[] fileVariants = super.getVariants();
            if (!isCompletion || getRangeInElement().getStartOffset() != 1) {
              return fileVariants;
            }
            return ArrayUtil.mergeArrays(fileVariants, collectCatalogVariants());
          }

          private Object @NotNull [] collectCatalogVariants() {
            List<LookupElement> elements = new ArrayList<>();
            final Project project = getElement().getProject();
            final List<JsonSchemaInfo> schemas = JsonSchemaService.Impl.get(project).getAllUserVisibleSchemas();
            for (JsonSchemaInfo schema : schemas) {
              LookupElementBuilder element = LookupElementBuilder.create(schema.getUrl(project))
                .withPresentableText(schema.getDescription())
                .withLookupString(schema.getDescription())
                .withIcon(AllIcons.General.Web)
                .withTypeText(schema.getDocumentation(), true);
              if (schema.getName() != null) element = element.withLookupString(schema.getName());
              if (schema.getDocumentation() != null) element = element.withLookupString(schema.getDocumentation());
              elements.add(PrioritizedLookupElement.withPriority(element, -1));
            }
            return elements.toArray();
          }
        };
      }
    }.getAllReferences());
  }

  static @Nullable PsiElement resolveForPath(PsiElement element, String text, boolean alwaysRoot) {
    final JsonSchemaService service = JsonSchemaService.Impl.get(element.getProject());
    final JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter splitter = new JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter(text);
    VirtualFile schemaFile = CompletionUtil.getOriginalOrSelf(element.getContainingFile()).getVirtualFile();
    if (splitter.isAbsolute()) {
      assert splitter.getSchemaId() != null;
      schemaFile = service.findSchemaFileByReference(splitter.getSchemaId(), schemaFile);
      if (schemaFile == null) return null;
    }

    PsiFile psiFile = element.getManager().findFile(schemaFile);

    final String normalized = normalizeId(splitter.getRelativePath());
    if (!alwaysRoot && (StringUtil.isEmptyOrSpaces(normalized) || split(normalizeSlashes(normalized)).isEmpty())
        || !(psiFile instanceof JsonFile)) {
      return psiFile;
    }
    final List<String> chain = split(normalizeSlashes(normalized));
    final JsonSchemaObject schemaObject = service.getSchemaObjectForSchemaFile(schemaFile);
    if (schemaObject == null) return null;

    JsonValue value = ((JsonFile)psiFile).getTopLevelValue();
    return value == null ? psiFile : new JsonPointerResolver(value, StringUtil.join(chain, "/")).resolve();
  }

  public static final class JsonSchemaIdReference extends JsonSchemaBaseReference<JsonValue> {
    private final String myText;

    private JsonSchemaIdReference(JsonValue element, String text) {
      super(element, getRange(element));
      myText = text;
    }

    private static @NotNull TextRange getRange(JsonValue element) {
      final TextRange range = element.getTextRange().shiftLeft(element.getTextOffset());
      return new TextRange(range.getStartOffset() + 1, range.getEndOffset() - 1);
    }

    @Override
    public @Nullable PsiElement resolveInner() {
      String id = null;
      if (Registry.is("json.schema.object.v2")) {
        JsonSchemaObject schemaRootOrNull = JsonSchemaObjectStorage.getInstance(myElement.getProject())
          .getComputedSchemaRootOrNull(myElement.getContainingFile().getVirtualFile());
        if (schemaRootOrNull instanceof RootJsonSchemaObject<?,?> rootJsonSchemaObject) {
          id = rootJsonSchemaObject.resolveId(myText);
        }
      }
      if (id == null)  {
        id = JsonCachedValues.resolveId(myElement.getContainingFile(), myText);
      }
      if (id == null) return null;
      return resolveForPath(myElement, "#" + id, false);
    }

    @Override
    public Object @NotNull [] getVariants() {
      return JsonCachedValues.getAllIdsInFile(myElement.getContainingFile()).toArray();
    }
  }

  static final class JsonPointerReference extends JsonSchemaBaseReference<JsonValue> {
    private final String myFullPath;

    JsonPointerReference(JsonValue element, TextRange textRange, String curPath) {
      super(element, textRange);
      myFullPath = curPath;
    }

    @Override
    public @NotNull String getCanonicalText() {
      return myFullPath;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
      return resolveForPath(myElement, getCanonicalText(), false);
    }

    @Override
    protected boolean isIdenticalTo(JsonSchemaBaseReference that) {
      return super.isIdenticalTo(that) && getRangeInElement().equals(that.getRangeInElement());
    }

    @Override
    public Object @NotNull [] getVariants() {
      String text = getCanonicalText();
      int index = text.indexOf(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED);
      if (index >= 0) {
        String part = text.substring(0, index);
        text = prepare(part);
        String prefix = null;
        PsiElement element = resolveForPath(myElement, text, true);
        int indexOfSlash = part.lastIndexOf('/');
        if (indexOfSlash != -1 && indexOfSlash < text.length() - 1) {
          prefix = text.substring(indexOfSlash + 1);
          element = resolveForPath(myElement, prepare(text.substring(0, indexOfSlash)), true);
        }
        String finalPrefix = prefix;
        if (element instanceof JsonObject) {
          return ((JsonObject)element).getPropertyList().stream()
            .filter(p -> p.getValue() instanceof JsonContainer && (finalPrefix == null || p.getName().startsWith(finalPrefix)))
            .map(p -> LookupElementBuilder.create(p, escapeForJsonPointer(p.getName()))
              .withIcon(getIcon(p.getValue()))).toArray();
        }
        else if (element instanceof JsonArray) {
          List<JsonValue> list = ((JsonArray)element).getValueList();
          List<Object> values = new LinkedList<>();
          for (int i = 0; i < list.size(); i++) {
            String stringValue = String.valueOf(i);
            if (prefix != null && !stringValue.startsWith(prefix)) continue;
            values.add(LookupElementBuilder.create(stringValue).withIcon(getIcon(list.get(i))));
          }
          return ContainerUtil.toArray(values, Object[]::new);
        }
      }

      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    private static Icon getIcon(JsonValue value) {
      if (value instanceof JsonObject) {
        return AllIcons.Json.Object;
      }
      else if (value instanceof JsonArray) {
        return AllIcons.Json.Array;
      }
      return IconManager.getInstance().getPlatformIcon(PlatformIcons.Property);
    }
  }

  private static @NotNull String prepare(String part) {
    return part.endsWith("#/") ? part : StringUtil.trimEnd(part, '/');
  }
}
