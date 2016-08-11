/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.jsonSchema.extension.schema;

import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaResourcesRootsProvider;
import com.jetbrains.jsonSchema.impl.JsonSchemaWalker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Irina.Chernushina on 7/7/2016.
 */
public class JsonSchemaDefinitionResolver {
  @Nullable private String myRef;
  @Nullable JsonSchemaObject mySchemaObject;

  @NotNull private final PsiElement myElement;
  @Nullable final String mySchemaId;
  private boolean myInCurrentFile;

  public JsonSchemaDefinitionResolver(@NotNull PsiElement element, @Nullable String schemaId) {
    myElement = element;
    mySchemaId = schemaId;
  }

  public JsonSchemaDefinitionResolver setSchemaObject(@NotNull final JsonSchemaObject value) {
    mySchemaObject = value;
    return this;
  }

  public JsonSchemaDefinitionResolver setRef(@NotNull final String value) {
    myRef = value;
    return this;
  }

  @Nullable
  public PsiElement doResolve() {
    PsiElement result = tryResolveByName();
    if (result != null) return result;
    if (mySchemaId == null) {
      return tryResolveBySchemaObject();
    }
    return null;
  }

  private PsiElement tryResolveBySchemaObject() {
    if (!(myElement.getParent() instanceof JsonProperty)) return null;
    final Ref<PsiElement> ref = new Ref<>();

    final String propertyName = ((JsonProperty)myElement.getParent()).getName();

    JsonSchemaService.Impl.getEx(myElement.getProject()).iterateSchemaObjects(
      myElement.getContainingFile().getVirtualFile(),
      object -> {
        final JsonSchemaWalker.CompletionSchemesConsumer consumer = new JsonSchemaWalker.CompletionSchemesConsumer() {
          @Override
          public void consume(boolean isName, @NotNull JsonSchemaObject schema) {
            if (!ref.isNull()) return;

            String definitionAddress = schema.getDefinitionAddress();
            if (!StringUtil.isEmptyOrSpaces(definitionAddress) && definitionAddress.startsWith("#")) {
              definitionAddress = definitionAddress.substring(1) + "/properties/" + propertyName;
              ref.set(resolveByPath(definitionAddress));
              if (!ref.isNull()) return;
            }
            List<JsonSchemaObject> list = new ArrayList<>();
            if (schema.getAllOf() != null) list.addAll(schema.getAllOf());
            if (schema.getAnyOf() != null) list.addAll(schema.getAnyOf());
            if (schema.getOneOf() != null) list.addAll(schema.getOneOf());
            for (JsonSchemaObject schemaObject : list) {
              definitionAddress = schemaObject.getDefinitionAddress();
              if (!StringUtil.isEmptyOrSpaces(definitionAddress) && definitionAddress.startsWith("#")) {
                definitionAddress = definitionAddress.substring(1) + "/properties/" + propertyName;
                ref.set(resolveByPath(definitionAddress));
                if (!ref.isNull()) return;
              }
            }
          }
        };

        final List<JsonSchemaWalker.Step> position = JsonSchemaWalker.findPosition(((JsonProperty)myElement.getParent()).getNameElement(), true);
        if (position == null || position.isEmpty()) return true; // to continue iteration
        JsonSchemaWalker.extractSchemaVariants(consumer, object, true, position);
        return ref.isNull();
      });
    return ref.get();
  }

  private PsiElement tryResolveByName() {
    if (myRef == null) initializeName();
    if (myRef == null) return null;

    return resolveByPath(myRef);
  }

  @Nullable
  private PsiElement resolveByPath(String referenceName) {
    final Project project = myElement.getProject();
    final FileBasedIndex index = FileBasedIndex.getInstance();
    final Ref<Pair<VirtualFile, Integer>> reference = new Ref<>();
    GlobalSearchScope filter = mySchemaId != null && myInCurrentFile ? GlobalSearchScope.fileScope(myElement.getContainingFile()) :
                               GlobalSearchScope.allScope(project);
    filter = JsonSchemaResourcesRootsProvider.enlarge(project, filter);

    index.processValues(JsonSchemaFileIndex.PROPERTIES_INDEX, referenceName, null, new FileBasedIndex.ValueProcessor<Integer>() {
      @Override
      public boolean process(VirtualFile file, Integer value) {
        if (mySchemaId != null) {
          if (!JsonSchemaService.Impl.getEx(project).checkFileForId(mySchemaId, file)) {
            return true;
          }
        }
        reference.set(Pair.create(file, value));
        return false;
      }
    }, filter);

    if (!reference.isNull()) {
      final Pair<VirtualFile, Integer> pair = reference.get();
      final PsiFile file = myElement.getManager().findFile(pair.getFirst());
      if (file != null) {
        return file.findElementAt(pair.getSecond());
      }
    }
    return null;
  }

  private void initializeName() {
    final List<String> names = new ArrayList<>();
    final PsiElement parent = myElement.getParent();
    if (!(parent instanceof JsonProperty)) return;
    JsonProperty element = (JsonProperty)parent;
    while (true) {
      names.add(StringUtil.unquoteString(element.getName()));
      if (!(element.getParent() instanceof JsonObject)) break;
      final PsiElement grand = element.getParent().getParent();
      if (grand instanceof JsonProperty && ((JsonProperty)grand).getValue() != null &&
          ((JsonProperty)grand).getValue().equals(element.getParent())) {
        element = (JsonProperty) grand;
      } else break;
    }
    final StringBuilder path = new StringBuilder();
    Collections.reverse(names);
    for (String name : names) {
      path.append("/properties/").append(name);
    }
    myRef = path.toString();
  }

  public JsonSchemaDefinitionResolver setInCurrentFile(boolean inCurrentFile) {
    myInCurrentFile = inCurrentFile;
    return this;
  }
}
