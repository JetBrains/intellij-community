/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonFileType;
import com.intellij.json.psi.*;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileInfoManager;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.JsonPointerUtil;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author Irina.Chernushina on 3/31/2016.
 */
public class JsonSchemaRefReferenceProvider extends PsiReferenceProvider {

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    List<PsiReference> refs = ContainerUtil.newArrayList();
    if (element instanceof JsonStringLiteral) {
      String originalText = element.getText();
      int hash = originalText.indexOf('#');
      List<Pair<TextRange, String>> fragments = ((JsonStringLiteral)element).getTextFragments();
      if (fragments.size() == 1) {
        Pair<TextRange, String> fragment = fragments.get(0);
        final JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter splitter = new JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter(fragment.second);
        String id = splitter.getSchemaId();
        if (id != null) {
          ContainerUtil.addAll(refs, new FileReferenceSet(id, element, 1, null, true,
                                                          true, new JsonFileType[] {JsonFileType.INSTANCE}) {
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
              if (hash != -1 && range.getStartOffset() >= hash) return null;
              if (hash != -1 && range.getEndOffset() > hash) {
                range = new TextRange(range.getStartOffset(), hash);
                text = text.substring(0, text.indexOf('#'));
              }
              return new FileReference(this, range, index, text) {
                @Override
                protected Object createLookupItem(PsiElement candidate) {
                  return FileInfoManager.getFileLookupItem(candidate);
                }
              };
            }
          }.getAllReferences());
        }
        String relativePath = JsonSchemaService.normalizeId(splitter.getRelativePath()).replace('\\', '/');
        List<String> parts1 = StringUtil.split(relativePath, "/");
        String[] strings = ContainerUtil.toArray(parts1, String[]::new);
        List<String> parts2 = StringUtil.split(originalText.substring(hash + 1).replace('\\', '/'), "/");
        if (strings.length == parts2.size()) {
          int start = hash + 2;
          for (int i = 0; i < parts2.size(); i++) {
            int length = parts2.get(i).length();
            if (i == parts2.size() - 1) length--;
            refs.add(new JsonSchemaRefReference((JsonValue)element, new TextRange(start, start + length),
                                                (id == null ? "" : id) + "#/" + StringUtil.join(strings, 0, i + 1, "/")));
            start += length + 1;
          }
        }
      }
    }
    return refs.size() == 0 ? PsiReference.EMPTY_ARRAY : ContainerUtil.toArray(refs, PsiReference[]::new);
  }

  private static class JsonSchemaRefReference extends JsonSchemaBaseReference<JsonValue> {
    private final String myFullPath;

    JsonSchemaRefReference(JsonValue element, TextRange textRange, String curPath) {
      super(element, textRange);
      myFullPath = curPath;
    }

    @NotNull
    @Override
    public String getCanonicalText() {
      return myFullPath;
    }

    @Nullable
    @Override
    public PsiElement resolveInner() {
      return resolveForPath(getCanonicalText(), false);
    }

    @Nullable
    private PsiElement resolveForPath(String text, boolean alwaysRoot) {
      final JsonSchemaService service = JsonSchemaService.Impl.get(getElement().getProject());
      final JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter splitter = new JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter(text);
      VirtualFile schemaFile = CompletionUtil.getOriginalOrSelf(getElement().getContainingFile()).getVirtualFile();
      if (splitter.isAbsolute()) {
        assert splitter.getSchemaId() != null;
        schemaFile = service.findSchemaFileByReference(splitter.getSchemaId(), schemaFile);
        if (schemaFile == null) return null;
      }

      final String normalized = JsonSchemaService.normalizeId(splitter.getRelativePath());
      if (!alwaysRoot && (StringUtil.isEmptyOrSpaces(normalized) || StringUtil.split(normalized.replace("\\", "/"), "/").size() == 0)) {
        return myElement.getManager().findFile(schemaFile);
      }
      final List<String> chain = StringUtil.split(normalized.replace("\\", "/"), "/");
      final JsonSchemaObject schemaObject = service.getSchemaObjectForSchemaFile(schemaFile);
      if (schemaObject == null) return null;

      JsonValue root = schemaObject.getJsonObject();
      final List<JsonSchemaVariantsTreeBuilder.Step> steps = JsonSchemaVariantsTreeBuilder.buildSteps(StringUtil.join(chain, "/"));
      for (JsonSchemaVariantsTreeBuilder.Step step : steps) {
        String name = step.getName();
        if (name != null) {
          if (!(root instanceof JsonObject)) return null;
          JsonProperty property = ((JsonObject)root).findProperty(name);
          root = property == null ? null : property.getValue();
        }
        else {
          int idx = step.getIdx();
          if (idx < 0 || !(root instanceof JsonArray)) return null;
          List<JsonValue> list = ((JsonArray)root).getValueList();
          if (idx >= list.size()) return null;
          root = list.get(idx);
        }
      }

      return root;
    }

    @Override
    protected boolean isIdenticalTo(JsonSchemaBaseReference that) {
      return super.isIdenticalTo(that) && getRangeInElement().equals(that.getRangeInElement());
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      String text = getCanonicalText();
      int index = text.indexOf(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED);
      if (index >= 0) {
        String part = text.substring(0, index);
        text = prepare(part);
        String prefix = null;
        PsiElement element = resolveForPath(text, true);
        int indexOfSlash = part.lastIndexOf('/');
        if (indexOfSlash != -1 && indexOfSlash < text.length() - 1 && indexOfSlash < index) {
          prefix = text.substring(indexOfSlash + 1);
          element = resolveForPath(prepare(text.substring(0, indexOfSlash)), true);
        }
        String finalPrefix = prefix;
        if (element instanceof JsonObject) {
          return ((JsonObject)element).getPropertyList().stream()
            .filter(p -> p.getValue() instanceof JsonContainer && (finalPrefix == null || p.getName().startsWith(finalPrefix)))
            .map(p -> LookupElementBuilder.create(p, JsonPointerUtil.escapeForJsonPointer(p.getName()))
            .withIcon(getIcon(p.getValue()))).toArray();
        }
        else if (element instanceof JsonArray) {
          List<JsonValue> list = ((JsonArray)element).getValueList();
          List<Object> values = ContainerUtil.newLinkedList();
          for (int i = 0; i < list.size(); i++) {
            String stringValue = String.valueOf(i);
            if (prefix != null && !stringValue.startsWith(prefix)) continue;
            values.add(LookupElementBuilder.create(stringValue).withIcon(getIcon(list.get(i))));
          }
          return ContainerUtil.toArray(values, Object[]::new);
        }
      }

      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    private static Icon getIcon(JsonValue value) {
      if (value instanceof JsonObject) {
        return AllIcons.Json.Object;
      }
      else if (value instanceof JsonArray) {
        return AllIcons.Json.Array;
      }
      return AllIcons.Nodes.Property;
    }
  }

  @NotNull
  private static String prepare(String part) {
    return part.endsWith("#/") ? part : StringUtil.trimEnd(part, '/');
  }
}
