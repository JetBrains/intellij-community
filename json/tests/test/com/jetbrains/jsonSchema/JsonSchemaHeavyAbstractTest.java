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
package com.jetbrains.jsonSchema;

import com.intellij.codeInsight.completion.CompletionTestCase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Irina.Chernushina on 12/5/2016.
 */
public abstract class JsonSchemaHeavyAbstractTest extends CompletionTestCase {
  private FileTypeManager myFileTypeManager;
  private List<JsonSchemaMappingsConfigurationBase.SchemaInfo> mySchemas;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFileTypeManager = FileTypeManager.getInstance();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> myFileTypeManager.associatePattern(JsonSchemaFileType.INSTANCE, "*Schema.json"));
    mySchemas = new ArrayList<>();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      WriteCommandAction.runWriteCommandAction(getProject(), () -> myFileTypeManager.removeAssociatedExtension(JsonSchemaFileType.INSTANCE, "*Schema.json"));
      final JsonSchemaMappingsProjectConfiguration instance = JsonSchemaMappingsProjectConfiguration.getInstance(getProject());
      for (JsonSchemaMappingsConfigurationBase.SchemaInfo schema : mySchemas) {
        instance.removeSchema(schema);
      }
    } finally {
      super.tearDown();
    }
  }

  public String getTestDataPath() {
    PathManagerEx.TestDataLookupStrategy strategy = PathManagerEx.guessTestDataLookupStrategy();
    if (strategy.equals(PathManagerEx.TestDataLookupStrategy.COMMUNITY)) {
      return PathManager.getHomePath() + "/json" + getBasePath() + "/";
    }
    return PathManager.getHomePath() + "/community/json" + getBasePath() + "/";
  }

  protected abstract String getBasePath();

  protected void skeleton(@NotNull final Callback callback)  throws Exception {
    callback.configureFiles();
    callback.registerSchemes();
    JsonSchemaService.Impl.get(getProject()).reset();
    doHighlighting();
    complete();
    callback.doCheck();
  }

  protected interface Callback {
    void registerSchemes();
    void configureFiles() throws Exception;
    void doCheck();
  }

  protected void addSchema(@NotNull final JsonSchemaMappingsConfigurationBase.SchemaInfo schema) {
    JsonSchemaMappingsProjectConfiguration.getInstance(getProject()).addSchema(schema);
    mySchemas.add(schema);
  }
}
