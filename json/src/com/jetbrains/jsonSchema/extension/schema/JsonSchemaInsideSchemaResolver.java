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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.jsonSchema.impl.JsonSchemaExportedDefinitions;
import com.jetbrains.jsonSchema.impl.JsonSchemaWalker;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
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
  @NotNull private final MultiMap<VirtualFile, String> myVisitedDefinitions = new MultiMap<VirtualFile, String>() {
    @NotNull
    @Override
    protected Collection<String> createCollection() {
      return new HashSet<>();
    }
  };

  public JsonSchemaInsideSchemaResolver(@NotNull Project project,
                                        @NotNull VirtualFile schemaFile,
                                        @NotNull String reference, @NotNull List<JsonSchemaWalker.Step> steps) {
    myProject = project;
    mySchemaFile = schemaFile;
    myReference = reference;
    mySteps = steps;
  }

  public PsiElement resolveInSchemaRecursively() {
    final ArrayDeque<Trinity<VirtualFile, List<JsonSchemaWalker.Step>, String>> queue = new ArrayDeque<>();
    queue.add(Trinity.create(mySchemaFile, mySteps, myReference));
    myVisitedDefinitions.putValue(mySchemaFile, myReference);
    while (!queue.isEmpty()) {
      final Trinity<VirtualFile, List<JsonSchemaWalker.Step>, String> trinity = queue.removeFirst();
      final VirtualFile schemaFile = trinity.getFirst();
      final String reference = JsonSchemaExportedDefinitions.normalizeId(trinity.getThird());
      final PsiElement element = new JsonSchemaByPropertyIndexResolver(reference, myProject, schemaFile).resolveByName();
      if (element != null) return element;
      final List<String> parts = ContainerUtil.filter(reference.replace("\\", "/").split("/"), s -> !StringUtil.isEmptyOrSpaces(s));
      final String shortName = parts.get(parts.size() - 1);
      final List<JsonSchemaWalker.Step> steps = trinity.getSecond();
      new JsonSchemaBySchemaObjectResolver(myProject, schemaFile, shortName, steps,
                                           (file, relativeReference) -> {
                                             final Pair<List<JsonSchemaWalker.Step>, String> innerSteps = JsonSchemaWalker.buildSteps(relativeReference);
                                             if (mySchemaFile.equals(file) &&
                                                 (myReference.equals(relativeReference) || myVisitedDefinitions.get(file).contains(relativeReference)))
                                               return;
                                             myVisitedDefinitions.putValue(file, relativeReference);
                                             queue.add(Trinity.create(file, innerSteps.getFirst(), relativeReference));
                                           }).iterateMatchingDefinitions();
    }
    return null;
  }
}
