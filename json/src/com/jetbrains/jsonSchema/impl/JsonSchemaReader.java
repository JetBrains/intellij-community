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
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonValue;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * @author Irina.Chernushina on 8/27/2015.
 */
public class JsonSchemaReader {
  public static final Logger LOG = Logger.getInstance("#com.jetbrains.jsonSchema.impl.JsonSchemaReader");
  public static final NotificationGroup ERRORS_NOTIFICATION = NotificationGroup.logOnlyGroup("JSON Schema");

  @NotNull private final JsonObject myRoot;

  public JsonSchemaReader(@NotNull JsonObject root) {
    myRoot = root;
  }

  @Nullable
  public static JsonSchemaReader create(@NotNull Project project, @NotNull VirtualFile key) {
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(key);
    if (!(psiFile instanceof JsonFile)) return null;
    final List<JsonValue> values = ((JsonFile)psiFile).getAllTopLevelValues();
    if (values.size() != 1 || !(values.get(0) instanceof JsonObject)) return null;
    return new JsonSchemaReader((JsonObject)values.get(0));
  }

  public JsonSchemaObject read() throws IOException {
    final ReadJsonSchemaFromPsi reader = new ReadJsonSchemaFromPsi();
    final JsonSchemaObject object = reader.read(myRoot);
    processReferences(object, reader.getAllObjects());
    final ArrayList<JsonSchemaObject> withoutDefinitions = new ArrayList<>(reader.getAllObjects());
    removeDefinitions(object, withoutDefinitions);
    return object;
  }

  public static boolean isJsonSchema(@NotNull Project project, @NotNull VirtualFile key,
                                     Consumer<String> errorConsumer) throws IOException {
    final JsonSchemaReader reader = create(project, key);
    if (reader == null) return false;
    try {
      reader.read();
    } catch (Exception e) {
      LOG.info(e);
      errorConsumer.consume(e.getMessage());
      return false;
    }
    return true;
  }

  private static void removeDefinitions(JsonSchemaObject root, ArrayList<JsonSchemaObject> objects) {
    final List<JsonSchemaObject> queue = new ArrayList<>(objects.size() + 1);
    queue.addAll(objects);
    queue.add(root);

    for (JsonSchemaObject object : queue) {
      final Map<String, JsonSchemaObject> definitions = object.getDefinitions();
      if (definitions != null) {
        objects.removeAll(definitions.values());
      }
    }
  }

  private void processReferences(JsonSchemaObject root, Set<JsonSchemaObject> objects) {
    final Set<String> queuedDefinitions = new HashSet<>();
    final ArrayDeque<JsonSchemaObject> queue = new ArrayDeque<>();
    queue.add(root);
    queue.addAll(objects);
    int control = 10000;

    while (!queue.isEmpty()) {
      if (--control == 0) {
        throw new RuntimeException("cyclic definitions search");
      }

      final JsonSchemaObject current = queue.removeFirst();
      if ("#".equals(current.getRef())) continue;
      if (current.getRef() != null) {
        final JsonSchemaObject definition = findDefinition(current.getRef(), root);
        if (definition == null) {
          current.setDefinitionAddress(current.getRef());
          // just skip current item
          current.setRef(null);
          continue;
        }
        if (definition.getRef() != null && !"#".equals(definition.getRef()) && !queuedDefinitions.contains(definition.getRef())) {
          queuedDefinitions.add(definition.getRef());
          queue.addFirst(current);
          queue.addFirst(definition);
          continue;
        }

        final JsonSchemaObject copy = new JsonSchemaObject(myRoot);
        copy.setDefinitionAddress(current.getRef());
        copy.mergeValues(definition);
        copy.mergeValues(current);
        copy.setPeerPointer(current.getPeerPointer());
        copy.setDefinitionsPointer(current.getDefinitionsPointer());
        current.copyValues(copy);
        current.setRef(null);
      }
    }
  }

  public static class SchemaUrlSplitter {
    @Nullable
    private final String mySchemaId;
    @NotNull
    private final String myRelativePath;

    public SchemaUrlSplitter(@NotNull final String ref) {
      if (isAbsoluteReference(ref)) {
        int idx = ref.indexOf("#/");
        if (idx == -1) {
          mySchemaId = ref.endsWith("#") ? ref.substring(0, ref.length() - 1) : ref;
          myRelativePath = "";
        } else {
          mySchemaId = ref.substring(0, idx);
          myRelativePath = ref.substring(idx);
        }
      } else {
        mySchemaId = null;
        myRelativePath = ref;
      }
    }

    public boolean isAbsolute() {
      return mySchemaId != null;
    }

    @Nullable
    public String getSchemaId() {
      return mySchemaId;
    }

    @NotNull
    public String getRelativePath() {
      return myRelativePath;
    }
  }

  @Nullable
  private static JsonSchemaObject findDefinition(@NotNull String ref,
                                                @NotNull final JsonSchemaObject root) {
    if ("#".equals(ref)) {
      return root;
    }
    if (isAbsoluteReference(ref)) {
      return null;
    }
    return findRelativeDefinition(ref, root);
  }

  static boolean isAbsoluteReference(@NotNull String ref) {
    return !ref.startsWith("#/");
  }

  @Nullable
  public static JsonSchemaObject findRelativeDefinition(@NotNull String ref, @NotNull JsonSchemaObject root) {
    if ("#".equals(ref)) {
      return root;
    }
    if (isAbsoluteReference(ref)) throw new RuntimeException("Non-relative or erroneous reference: " + ref);
    ref = ref.substring(2);
    final List<String> parts = StringUtil.split(ref, "/");
    JsonSchemaObject current = root;
    for (int i = 0; i < parts.size(); i++) {
      if (current == null) return null;
      final String part = parts.get(i);
      if ("definitions".equals(part)) {
        if (i == (parts.size() - 1)) throw new RuntimeException("Incorrect definition reference: " + ref);
        //noinspection AssignmentToForLoopParameter
        current = current.getDefinitions().get(parts.get(++i));
        continue;
      }
      if ("properties".equals(part)) {
        if (i == (parts.size() - 1)) throw new RuntimeException("Incorrect properties reference: " + ref);
        //noinspection AssignmentToForLoopParameter
        current = current.getProperties().get(parts.get(++i));
        continue;
      }

      current = current.getDefinitions().get(part);
    }
    if (current == null) return null;
    return current;
  }
}