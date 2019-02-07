// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.codeInsight.completion.CompletionTestCase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Irina.Chernushina on 12/5/2016.
 */
public abstract class JsonSchemaHeavyAbstractTest extends CompletionTestCase {
  private Map<String, UserDefinedJsonSchemaConfiguration> mySchemas;
  protected boolean myDoCompletion = true;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    //WriteCommandAction.runWriteCommandAction(getProject(), () -> myFileTypeManager.associatePattern(JsonSchemaFileType.INSTANCE, "*Schema.json"));
    mySchemas = new HashMap<>();
    myDoCompletion = true;
  }

  @Override
  public void tearDown() throws Exception {
    try {
      //WriteCommandAction.runWriteCommandAction(getProject(), () -> myFileTypeManager.removeAssociatedExtension(JsonSchemaFileType.INSTANCE, "*Schema.json"));
      final JsonSchemaMappingsProjectConfiguration instance = JsonSchemaMappingsProjectConfiguration.getInstance(getProject());
      instance.setState(Collections.emptyMap());
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
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
    JsonSchemaMappingsProjectConfiguration.getInstance(getProject()).setState(mySchemas);
    JsonSchemaService.Impl.get(getProject()).reset();
    getPsiManager().dropPsiCaches();
    doHighlighting();
    if (myDoCompletion) complete();
    callback.doCheck();
  }

  @NotNull
  protected static String getModuleDir(@NotNull final Project project) {
    String moduleDir = null;
    VirtualFile[] children = project.getBaseDir().getChildren();
    for (VirtualFile child : children) {
      if (child.isDirectory()) {
        moduleDir = child.getName();
        break;
      }
    }
    Assert.assertNotNull(moduleDir);
    return moduleDir;
  }

  protected interface Callback {
    void registerSchemes();
    void configureFiles() throws Exception;
    void doCheck() throws Exception;
  }

  protected void addSchema(@NotNull final UserDefinedJsonSchemaConfiguration schema) {
    mySchemas.put(schema.getName(), schema);
  }
}
