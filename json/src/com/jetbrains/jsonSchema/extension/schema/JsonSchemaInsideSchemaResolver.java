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
package com.jetbrains.jsonSchema.extension.schema;

import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaWalker;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Irina.Chernushina on 1/10/2017.
 */
public class JsonSchemaInsideSchemaResolver {
  public static final String PROPERTIES = "/properties/";
  @NotNull private final Project myProject;
  @NotNull private final VirtualFile mySchemaFile;
  @NotNull private final String myReference;
  @NotNull private final List<JsonSchemaWalker.Step> mySteps;

  public JsonSchemaInsideSchemaResolver(@NotNull Project project,
                                        @NotNull VirtualFile schemaFile,
                                        @NotNull String reference, @NotNull List<JsonSchemaWalker.Step> steps) {
    myProject = project;
    mySchemaFile = schemaFile;
    myReference = reference;
    mySteps = steps;
  }

  public PsiElement resolveInSchemaRecursively() {
    final Ref<PsiElement> ref = new Ref<>();
    final JsonSchemaWalker.CompletionSchemesConsumer consumer = new JsonSchemaWalker.CompletionSchemesConsumer() {
      @Override
      public void consume(boolean isName,
                          @NotNull JsonSchemaObject schema,
                          @NotNull VirtualFile schemaFile,
                          @NotNull List<JsonSchemaWalker.Step> steps) {
        if (!ref.isNull()) return;
        final PsiFile file = PsiManager.getInstance(myProject).findFile(mySchemaFile);
        if (file == null) return;
        final JsonObject jsonObject = schema.getPeerPointer().getElement();
        if (jsonObject != null && jsonObject.isValid()) {
          if (jsonObject.getParent() instanceof JsonProperty)
            ref.set(((JsonProperty)jsonObject.getParent()).getNameElement());
          else ref.set(jsonObject);
        }
      }

      @Override
      public void oneOf(boolean isName,
                        @NotNull List<JsonSchemaObject> list,
                        @NotNull VirtualFile schemaFile,
                        @NotNull List<JsonSchemaWalker.Step> steps) {
        list.stream().findFirst().ifPresent(object -> consume(isName, object, schemaFile, steps));
      }

      @Override
      public void anyOf(boolean isName,
                        @NotNull List<JsonSchemaObject> list,
                        @NotNull VirtualFile schemaFile,
                        @NotNull List<JsonSchemaWalker.Step> steps) {
        list.stream().findFirst().ifPresent(object -> consume(isName, object, schemaFile, steps));
      }
    };
    JsonSchemaService.Impl.get(myProject).visitSchemaObject(mySchemaFile,
                                                              object -> {
                                                                JsonSchemaWalker.extractSchemaVariants(
                                                                  myProject, consumer, mySchemaFile, object, true, mySteps, false);
                                                                return true;
                                                              });
    return ref.get();
  }
}
